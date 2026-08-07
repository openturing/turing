/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.dsl.eval;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.properties.TurCatalogCopilotProperty;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T814 / §LVIII.1 · T816 / §LVIII.3 (Block BJ) — serves the
 * {@link TurRankingLexicon} the {@link TurNLRankingPlanner} ranks with, read from
 * a resource bundle ({@code ranking/lexicon-<language>.json}) instead of compiled
 * into the planner, and resolved by the caller's locale.
 *
 * <p>A locale resolves to its language ({@code pt_BR} → {@code pt}); a language
 * with no bundle anywhere falls back to {@link TurRankingLexicon#DEFAULT_LANGUAGE}.
 * {@code turing.genai.copilot.ranking.lexicon-dir} names an external directory
 * whose {@code lexicon-<language>.json} is merged <em>over</em> the shipped one, so
 * a deployment adds a domain synonym — or a whole new language — as a mounted file.
 *
 * <p>Resolution is cached per language for the life of the bean: bundles are read
 * once, and building one costs a JSON parse, a regex compile and an analyzer.
 *
 * <p><strong>Fail-open by construction.</strong> An unreadable bundle is logged and
 * served as an empty lexicon, which resolves no direction and therefore no sort —
 * exactly the degradation a query with no superlative already takes. Ranking never
 * throws, and a broken lexicon never costs the caller its facet parse.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurRankingLexiconProvider {

    private static final String RESOURCE_PATTERN = "ranking/lexicon-%s.json";
    private static final String FILE_PATTERN = "lexicon-%s.json";
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    /**
     * How many distinct per-site overrides (T817) stay compiled. A deployment has
     * one per catalog site, so this is generous — and it is a cap rather than a
     * budget because the key is caller-supplied text.
     */
    static final int MAX_CACHED_OVERRIDES = 64;

    private final ConcurrentMap<String, Optional<TurRankingLexicon>> byLanguage =
            new ConcurrentHashMap<>();

    /**
     * T941 / §LVIII.6 — merged per-site lexicons, least-recently-used first out. The
     * cap bounds a map whose key is caller-supplied text; the <em>eviction</em> is
     * what keeps it from becoming a cliff, where one site past the cap rebuilt its
     * lexicon on every request.
     *
     * <p>Nothing closes what it evicts, deliberately: eviction is not unreachability.
     * A request that read a lexicon a moment ago still holds it, and closing that
     * analyzer would make an unrelated turn lose its sort. The analyzer becomes
     * collectible when the last holder drops it, which is the only moment that is
     * safe and the one no hook can observe.
     */
    private final Map<String, TurRankingLexicon> byOverride = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, TurRankingLexicon> eldest) {
                    return size() > MAX_CACHED_OVERRIDES;
                }
            });

    private final TurCatalogCopilotProperty copilotProperty;

    public TurRankingLexiconProvider(TurCatalogCopilotProperty copilotProperty) {
        this.copilotProperty = copilotProperty;
    }

    /** The default ({@code en}) lexicon — what T811's hardcoded sets became. */
    public TurRankingLexicon lexicon() {
        return lexicon(TurRankingLexicon.DEFAULT_LANGUAGE);
    }

    /**
     * The lexicon for {@code locale} — a language tag, a {@code pt_BR}-style locale
     * or a bare language — built and cached on first use. A language nothing
     * declares falls back to {@link TurRankingLexicon#DEFAULT_LANGUAGE}, and a
     * deployment with no English bundle either gets an empty lexicon rather than an
     * exception.
     */
    public TurRankingLexicon lexicon(String locale) {
        String language = language(locale);
        Optional<TurRankingLexicon> resolved = byLanguage.computeIfAbsent(language, this::load);
        if (resolved.isPresent()) {
            return resolved.get();
        }
        if (!TurRankingLexicon.DEFAULT_LANGUAGE.equals(language)) {
            log.debug("[RankingLexicon] no bundle for '{}' — falling back to '{}'", language,
                    TurRankingLexicon.DEFAULT_LANGUAGE);
            return lexicon(TurRankingLexicon.DEFAULT_LANGUAGE);
        }
        return TurRankingLexicon.of(TurRankingLexiconBundle.empty(language));
    }

    /**
     * T817 / §LVIII.4 — the {@code locale} lexicon with a site's own bundle merged
     * over it. {@code override} is a lexicon bundle in the resource JSON shape;
     * blank or unparseable, the resolved lexicon is returned unchanged, so a
     * mistyped override costs the site nothing but its extra words.
     */
    public TurRankingLexicon lexicon(String locale, String override) {
        if (!StringUtils.hasText(override)) {
            return lexicon(locale);
        }
        String key = language(locale) + "\n" + override;
        TurRankingLexicon cached = byOverride.get(key);
        if (cached != null) {
            return cached;
        }
        // Merged outside the map's lock: building one costs a JSON parse, a synonym
        // FST and two analyzers, and holding the lock for that would serialise every
        // other site's cache hit behind it. A racing duplicate build is the cheaper
        // of the two, and `put` makes the last one win for everybody after it.
        TurRankingLexicon merged = merge(locale, override);
        byOverride.put(key, merged);
        return merged;
    }

    private TurRankingLexicon merge(String locale, String override) {
        // Merge onto the bundle the locale actually resolved to, fallback included:
        // a site's override must extend the lexicon its questions are ranked with.
        String language = byLanguage.computeIfAbsent(language(locale), this::load).isPresent()
                ? language(locale)
                : TurRankingLexicon.DEFAULT_LANGUAGE;
        try {
            return TurRankingLexicon.of(baseBundle(language)
                    .mergedWith(MAPPER.readValue(override, TurRankingLexiconBundle.class)));
        } catch (Exception e) {
            log.warn("[RankingLexicon] override is unreadable: {} — using the '{}' bundle as is",
                    e.getMessage(), language);
            return lexicon(locale);
        }
    }

    /** The bundle behind an already-resolved lexicon, re-read so it can be merged. */
    private TurRankingLexiconBundle baseBundle(String language) {
        TurRankingLexiconBundle shipped = fromClasspath(language);
        TurRankingLexiconBundle external = fromLexiconDir(language);
        if (shipped == null) {
            return external == null ? TurRankingLexiconBundle.empty(language) : external;
        }
        return shipped.mergedWith(external);
    }

    /** {@code pt_BR} / {@code pt-BR} / {@code PT} all name the {@code pt} bundle. */
    private static String language(String locale) {
        if (!StringUtils.hasText(locale)) {
            return TurRankingLexicon.DEFAULT_LANGUAGE;
        }
        String tag = locale.trim().replace('_', '-');
        String language = Locale.forLanguageTag(tag).getLanguage();
        return language.isEmpty()
                ? tag.toLowerCase(Locale.ROOT)
                : language.toLowerCase(Locale.ROOT);
    }

    /**
     * The shipped bundle for {@code language} with the external one merged over it.
     * Empty when neither exists — which is what makes the {@code en} fallback a
     * decision the caller's language did not silently answer.
     */
    private Optional<TurRankingLexicon> load(String language) {
        TurRankingLexiconBundle shipped = fromClasspath(language);
        TurRankingLexiconBundle external = fromLexiconDir(language);
        if (shipped == null && external == null) {
            return Optional.empty();
        }
        TurRankingLexiconBundle bundle = shipped == null
                ? external
                : shipped.mergedWith(external);
        return Optional.of(TurRankingLexicon.of(bundle));
    }

    private TurRankingLexiconBundle fromClasspath(String language) {
        String resource = RESOURCE_PATTERN.formatted(language);
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            return in == null ? null : MAPPER.readValue(in, TurRankingLexiconBundle.class);
        } catch (Exception e) {
            log.warn("[RankingLexicon] bundle '{}' is unreadable: {} — ignoring it", resource,
                    e.getMessage());
            return null;
        }
    }

    private TurRankingLexiconBundle fromLexiconDir(String language) {
        String dir = copilotProperty.getRanking().getLexiconDir();
        if (!StringUtils.hasText(dir)) {
            return null;
        }
        Path file = Path.of(dir, FILE_PATTERN.formatted(language));
        if (!Files.isReadable(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            log.info("[RankingLexicon] merging external bundle '{}'", file);
            return MAPPER.readValue(in, TurRankingLexiconBundle.class);
        } catch (Exception e) {
            log.warn("[RankingLexicon] external bundle '{}' is unreadable: {} — ignoring it", file,
                    e.getMessage());
            return null;
        }
    }
}
