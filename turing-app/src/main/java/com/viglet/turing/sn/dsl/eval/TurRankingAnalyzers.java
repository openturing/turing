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

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.WordlistLoader;
import org.apache.lucene.analysis.miscellaneous.LengthFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.CharsRef;

import lombok.extern.slf4j.Slf4j;

/**
 * T815 / §LVIII.2 (Block BJ) — builds the Lucene {@link Analyzer}s the ranking
 * planner tokenizes with, replacing the hand-rolled split/expand/stopword code.
 *
 * <p>The <strong>matching</strong> analyzer is
 * {@code StandardTokenizer → LowerCaseFilter → LengthFilter(2) →
 * SynonymGraphFilter → StopFilter → SnowballFilter} and runs over <em>both</em>
 * the query and each declared field's name + description, so a query lemma meets a
 * field lemma after stemming: {@code pricing} and {@code price} collapse, and the
 * synonym filter — not a hand-written map lookup — does the adjective→concept
 * bridge.
 *
 * <p>The <strong>stem</strong> analyzer is the same pipeline without synonyms or
 * stopwords, and exists for one job: normalising the lexicon's own direction words
 * to the form the matching analyzer will produce. Running {@code affordable}
 * through the matching analyzer would also emit {@code price}, which would make
 * every query mentioning a price read as ascending.
 *
 * <p>Both the stemmer and its stopword set are named by the bundle's
 * {@code stemmer} field (a Snowball language such as {@code English}), so adding a
 * language is a resource, not a rebuild. An unknown name degrades to no stemming
 * and the bundle's own stopwords.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
final class TurRankingAnalyzers {

    /** Splits camelCase boundaries so {@code intelligenceIndex} → two tokens. */
    private static final Pattern CAMEL = Pattern.compile("([a-z0-9])([A-Z])");

    /** Everything the tokenizer should see as a separator ({@code _} included). */
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    /** Snowball's own stopword lists, addressed by the same language name. */
    private static final String SNOWBALL_STOPWORDS = "org/apache/lucene/analysis/snowball/%s_stop.txt";

    /** Field names and short queries only — a token below this is noise. */
    private static final int MIN_TOKEN_LENGTH = 2;

    private TurRankingAnalyzers() {
    }

    /**
     * The analyzer both the query and the declared fields are run through.
     * {@code protectedWords} are kept out of the stopword set: a ranking word the
     * language calls a stopword ("most", "more") is the one token the planner
     * cannot afford to lose.
     */
    static Analyzer matching(TurRankingLexiconBundle bundle, Collection<String> protectedWords) {
        SynonymMap synonyms = synonymMap(bundle.synonyms());
        CharArraySet stopwords = stopwords(bundle, protectedWords);
        String stemmer = bundle.stemmer();
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer source = new StandardTokenizer();
                TokenStream stream = new LowerCaseFilter(source);
                stream = new LengthFilter(stream, MIN_TOKEN_LENGTH, Integer.MAX_VALUE);
                if (synonyms != null) {
                    stream = new SynonymGraphFilter(stream, synonyms, true);
                }
                if (!stopwords.isEmpty()) {
                    stream = new StopFilter(stream, stopwords);
                }
                return new TokenStreamComponents(source, stem(stream, stemmer));
            }
        };
    }

    /**
     * The analyzer the lexicon's own direction words are normalised with — stemming
     * only, so {@code affordable} becomes {@code afford} and nothing else.
     */
    static Analyzer stemOnly(TurRankingLexiconBundle bundle) {
        String stemmer = bundle.stemmer();
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer source = new StandardTokenizer();
                TokenStream stream = new LowerCaseFilter(source);
                stream = new LengthFilter(stream, MIN_TOKEN_LENGTH, Integer.MAX_VALUE);
                return new TokenStreamComponents(source, stem(stream, stemmer));
            }
        };
    }

    /**
     * The distinct terms {@code value} analyses to, camelCase split first (Unicode
     * word-break keeps {@code intelligenceIndex} whole, and a declared field name is
     * exactly where that spelling shows up). Never throws — an analysis failure
     * yields no tokens, which the planner reads as "nothing resolved".
     */
    static Set<String> tokens(Analyzer analyzer, String value) {
        if (analyzer == null || value == null || value.isBlank()) {
            return Set.of();
        }
        String spaced = NON_WORD.matcher(CAMEL.matcher(value).replaceAll("$1 $2")).replaceAll(" ");
        Set<String> tokens = new LinkedHashSet<>();
        try (TokenStream stream = analyzer.tokenStream("ranking", new StringReader(spaced))) {
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                tokens.add(term.toString());
            }
            stream.end();
        } catch (IOException | RuntimeException e) {
            log.warn("[RankingLexicon] analysis of '{}' failed: {}", value, e.getMessage());
            return Set.of();
        }
        return tokens;
    }

    private static TokenStream stem(TokenStream stream, String stemmer) {
        if (stemmer == null || stemmer.isBlank()) {
            return stream;
        }
        try {
            return new SnowballFilter(stream, stemmer);
        } catch (RuntimeException e) {
            log.warn("[RankingLexicon] unknown Snowball stemmer '{}': {} — not stemming",
                    stemmer, e.getMessage());
            return stream;
        }
    }

    /**
     * The bundle's stopwords unioned with Snowball's list for the same language,
     * minus every word the ranking vocabulary depends on.
     */
    private static CharArraySet stopwords(TurRankingLexiconBundle bundle,
            Collection<String> protectedWords) {
        Set<String> words = new LinkedHashSet<>(bundle.stopwords());
        words.addAll(snowballStopwords(bundle.stemmer()));
        words.removeAll(Set.copyOf(protectedWords));
        return new CharArraySet(words, true);
    }

    private static Set<String> snowballStopwords(String stemmer) {
        if (stemmer == null || stemmer.isBlank()) {
            return Set.of();
        }
        String resource = SNOWBALL_STOPWORDS.formatted(stemmer.toLowerCase(Locale.ROOT));
        try (InputStream in = SnowballFilter.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                return Set.of();
            }
            Set<String> words = new LinkedHashSet<>();
            for (Object word : WordlistLoader.getSnowballWordSet(in)) {
                words.add(word instanceof char[] chars ? String.valueOf(chars) : word.toString());
            }
            return words;
        } catch (IOException | RuntimeException e) {
            log.warn("[RankingLexicon] Snowball stopwords '{}' unreadable: {}", resource,
                    e.getMessage());
            return Set.of();
        }
    }

    /**
     * The bundle's adjective→concept seed as a Lucene {@link SynonymMap}, original
     * tokens kept so a direction word survives its own expansion. {@code null} when
     * the bundle declares none.
     */
    private static SynonymMap synonymMap(Map<String, List<String>> synonyms) {
        if (synonyms.isEmpty()) {
            return null;
        }
        SynonymMap.Builder builder = new SynonymMap.Builder(true);
        boolean any = false;
        for (Map.Entry<String, List<String>> entry : synonyms.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                continue;
            }
            CharsRef trigger = new CharsRef(entry.getKey().trim().toLowerCase(Locale.ROOT));
            for (String value : entry.getValue()) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                builder.add(trigger, new CharsRef(value.trim().toLowerCase(Locale.ROOT)), true);
                any = true;
            }
        }
        try {
            return any ? builder.build() : null;
        } catch (IOException | RuntimeException e) {
            log.warn("[RankingLexicon] synonym map rejected: {} — not expanding", e.getMessage());
            return null;
        }
    }
}
