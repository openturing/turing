/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.genai.catalog.planning.TurCopilotQueryPlan;
import com.viglet.turing.genai.catalog.planning.TurCopilotQueryPlanner;
import com.viglet.turing.genai.catalog.planning.TurCopilotQueryPlanner.PlanRequest;
import com.viglet.turing.genai.catalog.planning.TurCopilotQueryPlannerFactory;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.sn.dsl.TurDslQuery;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.sn.dsl.TurDslSearchResponse;
import com.viglet.turing.sn.dsl.TurDslSearchService;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser;
import com.viglet.turing.sn.ranking.TurSNHybridRankingService;
import com.viglet.turing.system.TurDefaultChatModelResolver;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T392 / §XX.12 — the grounded conversational <strong>catalog copilot</strong>:
 * the GenAI value layer that ties Turing's structured search to the chat stack.
 *
 * <p>It composes the three Block&nbsp;R primitives into a "talk to the catalog"
 * assistant:
 * <ol>
 *   <li><b>NL→query planning (T385, T818)</b> — the user's prose is turned into a
 *       grounded {@link TurDslQueryRequest} over the site's <em>declared</em> field
 *       schema by the site's chosen {@link TurCopilotQueryPlanner} (it may only
 *       reference declared fields; it omits what it cannot ground). The strategy is
 *       a per-site option — {@code DETERMINISTIC} (the T811 ranking overlay plus a
 *       single {@link TurNLFacetParser} facet parse, the default),
 *       {@code LLM_ASSISTED} (multi-pass parse→judge→refine) or {@code HYBRID}
 *       (fast-path with LLM escalation) — resolved by
 *       {@link TurCopilotQueryPlannerFactory}.</li>
 *   <li><b>Hybrid SN search (T383)</b> — the grounded query is executed against
 *       the live index via {@link TurDslSearchService}; when the site runs a
 *       hybrid ranking mode the result page is reordered by
 *       {@link TurSNHybridRankingService} (objective signals only — RRF / rerank,
 *       never a commercial boost).</li>
 *   <li><b>RAG/agent stack</b> — the objectively-ranked, cited results become the
 *       grounding context for the default LLM, which answers <em>strictly</em>
 *       from those results and cites each claim as {@code [n]}.</li>
 * </ol>
 *
 * <p>Both Block&nbsp;R invariants are preserved: conservative grounding (the
 * answer never asserts what is not in the retrieved results, and the parser never
 * invents a field) and objective ranking (no paid placement ever enters the
 * pipeline). The whole path is <strong>fail-open</strong>: a parse failure
 * degrades to a free-text query, a hybrid-ranking failure degrades to the lexical
 * order, and an LLM failure still returns the matched citations so the client can
 * render results without a synthesized answer.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurCatalogCopilotService {

    /**
     * Maximum number of catalog results used as grounding context / citations. Shared
     * with the planning seam's {@code DEFAULT_TOP_K} so the page a plan retrieves and
     * the page the LLM is grounded on can never drift apart.
     */
    private static final int MAX_RESULTS = TurCopilotQueryPlanner.DEFAULT_TOP_K;
    /** Cap on the per-result snippet length fed to the answer LLM. */
    private static final int SNIPPET_CHARS = 320;
    /**
     * T798 / §LIV.9 — hard safety cap on how many citations {@link #answer} returns,
     * regardless of how many distinct {@code [n]} the answer emits. A cheap backstop
     * so a runaway answer can never flood the client with the whole catalog.
     */
    private static final int MAX_RETURNED_CITATIONS = 24;
    /** Matches a {@code [n]} citation marker in the LLM answer (1–4 digit rank). */
    private static final Pattern CITATION_MARKER = Pattern.compile("\\[(\\d{1,4})\\]");

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final TurNLFacetParser parser;
    private final TurCopilotQueryPlannerFactory plannerFactory;
    private final TurDslSearchService dslSearchService;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNHybridRankingService hybridRankingService;
    private final TurDefaultChatModelResolver chatModelResolver;
    private final com.viglet.turing.properties.TurCatalogCopilotProperty copilotProperty;

    public TurCatalogCopilotService(TurNLFacetParser parser,
            TurCopilotQueryPlannerFactory plannerFactory,
            TurDslSearchService dslSearchService,
            TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNHybridRankingService hybridRankingService,
            TurDefaultChatModelResolver chatModelResolver,
            com.viglet.turing.properties.TurCatalogCopilotProperty copilotProperty) {
        this.parser = parser;
        this.plannerFactory = plannerFactory;
        this.dslSearchService = dslSearchService;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.hybridRankingService = hybridRankingService;
        this.chatModelResolver = chatModelResolver;
        this.copilotProperty = copilotProperty;
    }

    /** True when a usable default LLM is configured (the copilot can answer). */
    public boolean isAvailable() {
        return parser.isAvailable();
    }

    // ─────────────────────────── Conversational answer ───────────────────────────

    /**
     * Answer a (possibly multi-turn) conversation grounded in {@code siteName}'s
     * catalog index. The latest user turn drives retrieval; the full history is
     * passed to the LLM so it can resolve conversational follow-ups.
     *
     * @param siteName the catalog SN site to talk to
     * @param locale   locale code (may be null — falls back to {@code en})
     * @param messages the conversation so far (role {@code user}/{@code assistant})
     */
    public TurCatalogCopilotResult answer(String siteName, String locale, List<ChatTurn> messages) {
        if (messages == null || messages.isEmpty()) {
            return TurCatalogCopilotResult.error("No conversation messages provided");
        }
        Optional<ChatModel> chatModelOpt = chatModelResolver.resolve();
        if (chatModelOpt.isEmpty()) {
            return TurCatalogCopilotResult.error("No usable default LLM configured for the catalog copilot");
        }
        String latestUser = latestUserMessage(messages);
        if (!StringUtils.hasText(latestUser)) {
            return TurCatalogCopilotResult.error("No user message to answer");
        }

        // T793 / §LIV.4 — stuff-all: when the whole catalog fits the token budget,
        // skip NL→DSL retrieval and ground the LLM on every row (still cited).
        // Falls back to filtered retrieval when the corpus is too large / empty.
        Retrieval retrieval = stuffAllRetrieve(siteName, locale)
                .orElseGet(() -> retrieve(siteName, locale, latestUser));
        if (!retrieval.siteFound()) {
            return TurCatalogCopilotResult.error("Catalog site not found: " + siteName);
        }

        try {
            String raw = callLlm(chatModelOpt.get(), siteName, retrieval, messages);
            // T798 / §LIV.9 + T800 / §LIV.10 — the LLM grounds on the whole retrieval
            // bundle (all rows in stuff-all mode), but the citations returned to the
            // client must reflect only what the answer actually cites, AND be
            // renumbered to compact answer-order footnotes (the raw ranks are
            // positions in the full catalog — [201], [230] — meaningless to a reader).
            GroundedAnswer grounded = groundCitations(raw, retrieval.citations());
            return TurCatalogCopilotResult.ok(grounded.answer(), grounded.citations(),
                    retrieval.groundedSummary(), retrieval.totalHits());
        } catch (RuntimeException e) {
            log.warn("[CatalogCopilot] answer LLM call failed for site '{}': {} — returning citations only",
                    siteName, e.getMessage());
            // No answer text to ground against — still bound the returned list to a
            // top-k slice so a failed stuff-all answer never floods the client.
            return TurCatalogCopilotResult.degraded(groundCitations(null, retrieval.citations()).citations(),
                    retrieval.groundedSummary(), retrieval.totalHits(),
                    "Answer generation failed: " + e.getMessage());
        }
    }

    /**
     * T798 / §LIV.9 + T800 / §LIV.10 — grounds the <em>returned</em> citations to
     * what the answer actually cites, and renumbers both the citations and the
     * answer's {@code [n]} markers to compact, answer-order footnotes ({@code 1..k}).
     *
     * <p>Grounding (T798): the distinct {@code [n]} markers the LLM emitted (the
     * system prompt mandates one per claim) select which citations are returned —
     * only those the answer names, not the whole grounding set. This matters most
     * for the T793 stuff-all path, which grounds the LLM on every catalog row:
     * without it, {@code answer()} would return the whole catalog as "Cited models".
     *
     * <p>Renumbering (T800): in stuff-all mode a raw rank is the row's position in
     * the entire catalog — the LLM cites {@code [201]}, {@code [230]}, which leak
     * into the rendered prose as meaningless big numbers. So each cited rank is
     * remapped to its 1-based order of first appearance, the answer text is
     * rewritten to those footnotes ({@code [201]→[1]}), and the citations are
     * returned in matching footnote order with {@code rank = footnote}. The client
     * then renders {@code [1] [2] …} as links to the correspondingly-numbered chips.
     *
     * <p>Fail-open: when no {@code [n]} is parseable (or none of the parsed ranks
     * match a citation), degrade to the filtered top-k slice — never back to all
     * rows — renumbered {@code 1..k}, with the (marker-less) prose left untouched. A
     * {@link #MAX_RETURNED_CITATIONS} backstop bounds the list regardless.
     */
    private GroundedAnswer groundCitations(String answer, List<TurCatalogCitation> all) {
        if (all == null || all.isEmpty()) {
            return new GroundedAnswer(answer, List.of());
        }
        Map<Integer, TurCatalogCitation> byRank = new LinkedHashMap<>();
        for (TurCatalogCitation citation : all) {
            byRank.putIfAbsent(citation.rank(), citation);
        }
        // Cited ranks in order of first appearance (parseCitedRanks preserves it),
        // kept only when they actually back a citation, capped by the backstop.
        Map<Integer, Integer> remap = new LinkedHashMap<>(); // raw rank -> compact footnote
        List<TurCatalogCitation> grounded = new ArrayList<>();
        for (int rawRank : parseCitedRanks(answer)) {
            if (!byRank.containsKey(rawRank) || grounded.size() >= MAX_RETURNED_CITATIONS) {
                continue;
            }
            int footnote = grounded.size() + 1;
            remap.put(rawRank, footnote);
            grounded.add(renumber(byRank.get(rawRank), footnote));
        }
        if (!grounded.isEmpty()) {
            return new GroundedAnswer(rewriteMarkers(answer, remap), List.copyOf(grounded));
        }
        // Fail-open: no usable markers — bounded top-k, renumbered 1..k, prose as-is.
        int cap = Math.min(MAX_RESULTS, all.size());
        List<TurCatalogCitation> slice = new ArrayList<>(cap);
        for (int i = 0; i < cap; i++) {
            slice.add(renumber(all.get(i), i + 1));
        }
        return new GroundedAnswer(answer, List.copyOf(slice));
    }

    /** A citation with its {@code rank} set to {@code footnote} (identity when equal). */
    private static TurCatalogCitation renumber(TurCatalogCitation c, int footnote) {
        return c.rank() == footnote ? c
                : new TurCatalogCitation(footnote, c.id(), c.title(), c.url(), c.score(),
                        c.rankingExplanation());
    }

    /**
     * Rewrites every {@code [n]} marker in the answer to its compact footnote from
     * {@code remap}; a marker with no mapping (an out-of-range or dangling rank the
     * LLM emitted for a row it wasn't given) is dropped so no orphan number reaches
     * the reader. Returns the text unchanged when there is nothing to rewrite.
     */
    private static String rewriteMarkers(String answer, Map<Integer, Integer> remap) {
        if (!StringUtils.hasText(answer)) {
            return answer;
        }
        Matcher matcher = CITATION_MARKER.matcher(answer);
        StringBuilder sb = new StringBuilder(answer.length());
        while (matcher.find()) {
            Integer footnote = null;
            try {
                footnote = remap.get(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // marker out of int range — treat as unmapped (dropped)
            }
            matcher.appendReplacement(sb, footnote != null ? "[" + footnote + "]" : "");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * The answer text with its {@code [n]} markers renumbered to compact footnotes,
     * paired with the cited citations in matching footnote order ({@code rank = n}).
     */
    private record GroundedAnswer(String answer, List<TurCatalogCitation> citations) {
    }

    /** Extracts the distinct 1-based ranks referenced as {@code [n]} in the answer. */
    private static Set<Integer> parseCitedRanks(String answer) {
        if (!StringUtils.hasText(answer)) {
            return Set.of();
        }
        Set<Integer> ranks = new LinkedHashSet<>();
        Matcher matcher = CITATION_MARKER.matcher(answer);
        while (matcher.find()) {
            try {
                int rank = Integer.parseInt(matcher.group(1));
                if (rank >= 1) {
                    ranks.add(rank);
                }
            } catch (NumberFormatException ignored) {
                // marker out of int range — skip it
            }
        }
        return ranks;
    }

    private String callLlm(ChatModel chatModel, String siteName, Retrieval retrieval,
            List<ChatTurn> history) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt(siteName, retrieval)));
        for (ChatTurn turn : history) {
            if ("assistant".equalsIgnoreCase(turn.role())) {
                messages.add(new AssistantMessage(turn.content()));
            } else {
                messages.add(new UserMessage(turn.content()));
            }
        }
        var result = chatModel.call(new Prompt(messages)).getResult();
        var output = result != null ? result.getOutput() : null;
        return output != null && output.getText() != null ? output.getText() : "";
    }

    private String systemPrompt(String siteName, Retrieval retrieval) {
        String context = retrieval.contextBlocks().isEmpty()
                ? "(no matching catalog results)"
                : String.join("\n\n", retrieval.contextBlocks());
        return """
                You are the catalog assistant for the "%s" catalog. Answer the user \
                STRICTLY from the CATALOG RESULTS listed below — they were retrieved \
                from the search index and are already ordered for the question (by \
                objective relevance, or by the field the user ranked on).

                HARD RULES:
                - Use ONLY the information in CATALOG RESULTS. Never invent items, \
                  values, attributes, or links that are not present below.
                - When the user asks for a superlative ("best", "top", "highest", \
                  "lowest", "most" or "least" by some quality), rank by the field \
                  whose LABEL names that quality as the OVERALL metric. Never treat \
                  a narrower per-category sub-metric as the overall metric just \
                  because its number is larger. Each value below is labelled by what \
                  it means; rank by the labelled metric, not the biggest number.
                - Cite every claim with the bracketed number of the result it comes \
                  from, e.g. [1], [2]. Cite multiple when relevant ([1][3]). The \
                  bracketed number is the ONLY citation form: never write source \
                  names, vendors, ranks, ids or labels such as "[Source: X]", \
                  "(Vendor)" or "[42]" as prose — the client renders each [n] as a \
                  footnote linking to the cited result, so a stray label is noise.
                - If the CATALOG RESULTS do not contain a relevant answer, say so \
                  plainly and suggest how the user could refine the search. Do not \
                  guess.
                - Be concise. Respond in the user's language.

                APPLIED FILTERS: %s

                CATALOG RESULTS:
                %s
                """.formatted(siteName,
                StringUtils.hasText(retrieval.groundedSummary()) ? retrieval.groundedSummary() : "(none)",
                context);
    }

    // ─────────────────────────── Retrieval (shared with the tool) ───────────────────────────

    /**
     * The shared retrieval step both the copilot endpoint and the
     * {@code catalog_search} agent tool use: ground the prose against the site
     * schema, execute it, apply hybrid ranking, and project the page into
     * citations + per-result context blocks. Never throws — degrades fail-open.
     */
    public Retrieval retrieve(String siteName, String locale, String query) {
        Optional<TurSNSite> siteOpt = turSNSiteRepository.findByNameIgnoreCase(siteName);
        if (siteOpt.isEmpty()) {
            return Retrieval.notFound();
        }
        TurSNSite site = siteOpt.get();
        List<TurNLFacetField> schema = resolveSchema(site);
        String searchLocale = StringUtils.hasText(locale) ? locale : "en";

        // T818 / §LIX.1 (Block BK) — planning is a seam now: the site's resolved
        // strategy (DETERMINISTIC = today's T811 overlay + LLM facet parse |
        // LLM_ASSISTED = parse→judge→refine | HYBRID = fast-path + escalation) owns
        // turning the prose into a structured query.
        TurCopilotQueryPlannerFactory.Resolution resolution = plannerFactory.resolve(siteName);
        TurCopilotQueryPlanner planner = resolution.planner();
        PlanRequest planRequest =
                new PlanRequest(siteName, searchLocale, query, schema, resolution.maxPasses());
        TurCopilotQueryPlan plan = planner.plan(planRequest);
        // T823 / §LX.2 — the filtered path was opaque: log the plan (strategy, LLM
        // passes, notes) AND the exact structured query executed, so a "no results"
        // answer is traceable to the query that produced it.
        logPlan(siteName, plan);
        Optional<TurDslSearchResponse> responseOpt =
                dslSearchService.search(siteName, searchLocale, plan.request());

        // T820 / §LIX.3 — second chance: a strategy may escalate once, having seen how
        // many hits its plan actually produced (HYBRID rescues an empty/degenerate one).
        Optional<TurCopilotQueryPlan> escalated =
                planner.replan(planRequest, plan, totalHitsOf(responseOpt));
        if (escalated.isPresent()) {
            plan = escalated.get();
            logPlan(siteName, plan);
            responseOpt = dslSearchService.search(siteName, searchLocale, plan.request());
        }

        TurDslQueryRequest request = plan.request();
        if (responseOpt.isEmpty() || responseOpt.get().hits() == null
                || responseOpt.get().hits().hits() == null) {
            log.info("[CatalogCopilot] site '{}' FILTERED returned NO hits (filters: {})",
                    siteName, blankToNone(summarize(request)));
            return new Retrieval(List.of(), List.of(), 0L, summarize(request), true);
        }

        TurDslSearchResponse response = responseOpt.get();
        List<TurDslSearchResponse.Hit> hits = response.hits().hits();
        long totalHits = response.hits().total() != null ? response.hits().total().value() : hits.size();
        log.info("[CatalogCopilot] site '{}' FILTERED returned {} hits (filters: {})",
                siteName, totalHits, blankToNone(summarize(request)));

        List<TurDslSearchResponse.Hit> ordered = applyHybridRanking(site, searchLocale, query, hits);

        Projection projection = project(ordered, MAX_RESULTS, fieldDescriptions(schema));
        return new Retrieval(projection.citations(), projection.contextBlocks(),
                totalHits, summarize(request), true);
    }

    /** T823 / §LX.2 + T818 — one INFO line per executed plan (strategy, cost, query). */
    private void logPlan(String siteName, TurCopilotQueryPlan plan) {
        log.info("[CatalogCopilot] site '{}' FILTERED mode: strategy={}, llmPasses={}, plan={}, query={}",
                siteName, plan.strategy(), plan.llmPasses(), plan.notes(), toJson(plan.request()));
    }

    /** The total hit count a response carries, or {@code 0} when it holds none. */
    private static long totalHitsOf(Optional<TurDslSearchResponse> responseOpt) {
        if (responseOpt.isEmpty() || responseOpt.get().hits() == null
                || responseOpt.get().hits().hits() == null) {
            return 0L;
        }
        TurDslSearchResponse.Hits hits = responseOpt.get().hits();
        return hits.total() != null ? hits.total().value() : hits.hits().size();
    }

    /**
     * Projects up to {@code max} hits (in the given order) into 1-based citations
     * and per-result grounding context blocks. Shared by the filtered
     * {@link #retrieve} path ({@code max = MAX_RESULTS}) and the T793 stuff-all
     * path ({@code max =} all hits).
     */
    private Projection project(List<TurDslSearchResponse.Hit> ordered, int max,
            Map<String, String> fieldDescriptions) {
        return projectWith(ordered, max,
                (rank, title, url, source) -> contextBlock(rank, title, url, source, fieldDescriptions));
    }

    /**
     * T822 / §LX.1 — the <strong>compact</strong> projection for stuff-all: one lean
     * line per row ({@code [rank] title | key: value | …}) over only the declared
     * <em>key</em> fields ({@code keyFields}), with no snippet and no per-field
     * descriptions. It is ~10× smaller than {@link #project} per row, so a catalog too
     * large for the verbose projection can still be grounded whole. Citations are
     * identical (the client chips are unaffected); only the LLM grounding text shrinks.
     */
    private Projection projectCompact(List<TurDslSearchResponse.Hit> ordered, int max,
            Set<String> keyFields) {
        return projectWith(ordered, max,
                (rank, title, url, source) -> compactContextBlock(rank, title, source, keyFields));
    }

    /** Shared projection loop: 1-based citations + per-row blocks from {@code builder}. */
    private Projection projectWith(List<TurDslSearchResponse.Hit> ordered, int max,
            ContextBlockBuilder builder) {
        List<TurCatalogCitation> citations = new ArrayList<>();
        List<String> contextBlocks = new ArrayList<>();
        int limit = Math.min(max, ordered.size());
        for (int i = 0; i < limit; i++) {
            TurDslSearchResponse.Hit hit = ordered.get(i);
            int rank = i + 1;
            Map<String, Object> source = hit.source() != null ? hit.source() : Map.of();
            String title = firstNonBlank(str(source.get(TurSNFieldName.TITLE)), hit.id());
            String url = str(source.get(TurSNFieldName.URL));
            citations.add(new TurCatalogCitation(rank, hit.id(), title,
                    StringUtils.hasText(url) ? url : null, hit.score(),
                    hit.explanation()));
            contextBlocks.add(builder.build(rank, title, url, source));
        }
        return new Projection(citations, contextBlocks);
    }

    /** Builds one row's grounding context block (verbose or compact). */
    @FunctionalInterface
    private interface ContextBlockBuilder {
        String build(int rank, String title, String url, Map<String, Object> source);
    }

    /**
     * T822 / §LX.1 — the declared <em>key</em> field names for the compact projection:
     * the numeric (sortable) and facet fields — the ones a reader ranks or filters on —
     * derived from the manifest's {@code sortable}/{@code facet} flags
     * ({@link TurNLFacetField#numeric()} / {@link TurNLFacetField#facet()}). Free-text
     * and other descriptive fields are dropped from the compact line.
     */
    private static Set<String> compactKeyFields(List<TurNLFacetField> schema) {
        Set<String> keys = new LinkedHashSet<>();
        if (schema != null) {
            for (TurNLFacetField field : schema) {
                if (field != null && field.name() != null && (field.numeric() || field.facet())) {
                    keys.add(field.name());
                }
            }
        }
        return keys;
    }

    /**
     * T822 / §LX.1 — one compact line for a row: {@code [rank] title} followed by each
     * present key field as {@code | key: value} (values capped like the verbose block).
     * No snippet, no descriptions — the flat key is self-descriptive enough
     * ({@code pricing_inputPer1M}, {@code contextWindow}) for whole-catalog reasoning.
     */
    private String compactContextBlock(int rank, String title, Map<String, Object> source,
            Set<String> keyFields) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(rank).append("] ").append(title);
        for (String key : keyFields) {
            Object raw = source.get(key);
            if (raw == null) {
                continue;
            }
            String value = str(raw);
            if (StringUtils.hasText(value) && value.length() <= 80) {
                sb.append(" | ").append(key).append(": ").append(value);
            }
        }
        return sb.toString();
    }

    /**
     * T799 / §LIV — the flat-key → declared-description map fed to
     * {@link #contextBlock}. The stuff-all context printed each attribute as its
     * cryptic flattened key ({@code benchmarks_scores_math_value: 93.4} sitting
     * next to {@code benchmarks_intelligenceIndex: 23.8}), so the LLM grabbed the
     * biggest number and mistook a per-domain sub-score for the overall metric.
     * Labelling each value by the field's declared {@code description} (sharpened
     * upstream so the headline index reads as THE metric and each domain score as
     * "sub-score only") lets the model rank by what a field <em>means</em>, not by
     * a cryptic name. Only fields with a non-blank description are mapped; the rest
     * fall back to the raw key.
     */
    private static Map<String, String> fieldDescriptions(List<TurNLFacetField> schema) {
        Map<String, String> descriptions = new LinkedHashMap<>();
        if (schema != null) {
            for (TurNLFacetField field : schema) {
                if (field != null && field.name() != null && StringUtils.hasText(field.description())) {
                    descriptions.put(field.name(), field.description().trim());
                }
            }
        }
        return descriptions;
    }

    /**
     * T793 / §LIV.4 (Block BF) — <strong>stuff-all</strong> retrieval: when the
     * whole catalog fits within the configured token budget (and document cap),
     * skip the NL→DSL parse entirely and ground the LLM on <em>every</em> row via
     * a {@code match_all} query — the extreme-vectorless path where a small catalog
     * fits in one prompt. Returns {@link Optional#empty()} (so the caller falls
     * back to filtered {@link #retrieve}) when disabled, when the site/index is
     * empty, when the index holds more rows than were fetched (partial → not "all"),
     * or when the assembled context exceeds the budget. Never throws.
     */
    Optional<Retrieval> stuffAllRetrieve(String siteName, String locale) {
        if (!copilotProperty.isStuffAllEnabled()) {
            return Optional.empty();
        }
        Optional<TurSNSite> siteOpt = turSNSiteRepository.findByNameIgnoreCase(siteName);
        if (siteOpt.isEmpty()) {
            return Optional.empty();
        }
        String searchLocale = StringUtils.hasText(locale) ? locale : "en";
        int maxDocs = Math.max(1, copilotProperty.getStuffAllMaxDocs());
        Optional<TurDslSearchResponse> responseOpt =
                dslSearchService.search(siteName, searchLocale, matchAllRequest(maxDocs));
        if (responseOpt.isEmpty() || responseOpt.get().hits() == null
                || responseOpt.get().hits().hits() == null
                || responseOpt.get().hits().hits().isEmpty()) {
            return Optional.empty();
        }
        TurDslSearchResponse response = responseOpt.get();
        List<TurDslSearchResponse.Hit> hits = response.hits().hits();
        long totalHits = response.hits().total() != null ? response.hits().total().value() : hits.size();
        // Only stuff when we actually hold the entire catalog — a partial page
        // (index bigger than the fetch cap) must use filtered retrieval instead.
        if (totalHits > hits.size()) {
            // T823 / §LX.2 — make the decision visible at INFO (was silent).
            log.info("[CatalogCopilot] site '{}' stuff-all SKIPPED: index has {} rows > fetched {} "
                    + "(stuffAllMaxDocs={}) -> FILTERED retrieval",
                    siteName, totalHits, hits.size(), maxDocs);
            return Optional.empty();
        }
        // T799 / §LIV — resolve the declared schema so stuff-all context blocks
        // label each value by its field description, not its cryptic flat key.
        List<TurNLFacetField> schema = resolveSchema(siteOpt.get());
        int budget = copilotProperty.getStuffAllTokenBudget();

        // T793 — try the full (verbose, described) projection first: richest grounding.
        Projection full = project(hits, hits.size(), fieldDescriptions(schema));
        int fullTokens = estimateTokens(full.contextBlocks());
        if (fullTokens <= budget) {
            log.info("[CatalogCopilot] site '{}' STUFF_ALL (full): grounding on all {} rows "
                    + "(~{} tokens, budget {})", siteName, hits.size(), fullTokens, budget);
            return Optional.of(new Retrieval(full.citations(), full.contextBlocks(),
                    totalHits, "", true));
        }

        // T822 / §LX.1 — full is over budget; retry a COMPACT projection (key fields
        // only, no snippet / no descriptions, ~10× smaller per row) so a large catalog
        // still gets grounded whole instead of silently dropping to filtered retrieval.
        Projection compact = projectCompact(hits, hits.size(), compactKeyFields(schema));
        int compactTokens = estimateTokens(compact.contextBlocks());
        if (compactTokens <= budget) {
            log.info("[CatalogCopilot] site '{}' STUFF_ALL (compact): grounding on all {} rows "
                    + "(~{} tokens, budget {}; full was ~{})",
                    siteName, hits.size(), compactTokens, budget, fullTokens);
            return Optional.of(new Retrieval(compact.citations(), compact.contextBlocks(),
                    totalHits, "", true));
        }

        // T823 / §LX.2 — even the compact whole-catalog exceeds the budget → filtered.
        // Log it at INFO with the numbers so the fallback is diagnosable (was silent).
        log.info("[CatalogCopilot] site '{}' stuff-all OVER BUDGET even compact: ~{} tokens > {} "
                + "(rows={}, full ~{}) -> FILTERED retrieval (raise "
                + "turing.genai.copilot.stuffAllTokenBudget)",
                siteName, compactTokens, budget, hits.size(), fullTokens);
        return Optional.empty();
    }

    /**
     * T818 / §LIX.1 — the bounded {@code match_all} the stuff-all path uses; the
     * planning-side bodies now live in
     * {@link com.viglet.turing.genai.catalog.planning.TurCopilotQueryBodies}.
     */
    private TurDslQueryRequest matchAllRequest(int size) {
        return com.viglet.turing.genai.catalog.planning.TurCopilotQueryBodies.matchAll(size);
    }

    /** Rough token estimate (chars/4) of the assembled grounding context. */
    private static int estimateTokens(List<String> contextBlocks) {
        int chars = 0;
        for (String block : contextBlocks) {
            chars += block == null ? 0 : block.length();
        }
        return chars / 4;
    }

    /** Citations + per-result context blocks projected from a hit page. */
    private record Projection(List<TurCatalogCitation> citations, List<String> contextBlocks) {
    }

    /**
     * Reorders the lexical hit page through {@link TurSNHybridRankingService} when
     * the site runs a hybrid mode. The hybrid service works on {@link TurSEResult},
     * so we adapt the DSL hits in, fuse, then map the reordered ids back to their
     * original hits (preserving score and source). Fail-open: returns the input
     * order on any mismatch.
     */
    private List<TurDslSearchResponse.Hit> applyHybridRanking(TurSNSite site, String locale,
            String query, List<TurDslSearchResponse.Hit> hits) {
        if (!hybridRankingService.isEnabled(site) || hits.size() < 2) {
            return hits;
        }
        Map<String, TurDslSearchResponse.Hit> byId = new LinkedHashMap<>();
        List<TurSEResult> page = buildRankingPage(hits, byId);
        Locale parsedLocale = safeLocale(locale);
        List<TurSEResult> reordered = hybridRankingService.fuse(site, parsedLocale, query, page);
        List<TurDslSearchResponse.Hit> result = reorderByFusion(reordered, byId, hits.size());
        result.addAll(byId.values()); // anything the pipeline didn't cover keeps tail order
        return result.isEmpty() ? hits : result;
    }

    /**
     * Projects the (de-duplicated) hits into {@link TurSEResult} page entries for
     * the ranking pipeline, populating {@code byId} with the source hit per id.
     */
    private List<TurSEResult> buildRankingPage(List<TurDslSearchResponse.Hit> hits,
            Map<String, TurDslSearchResponse.Hit> byId) {
        List<TurSEResult> page = new ArrayList<>(hits.size());
        for (TurDslSearchResponse.Hit hit : hits) {
            if (hit.id() == null || byId.containsKey(hit.id())) {
                continue;
            }
            byId.put(hit.id(), hit);
            Map<String, Object> fields = new LinkedHashMap<>();
            if (hit.source() != null) {
                fields.putAll(hit.source());
            }
            fields.put(TurSNFieldName.ID, hit.id());
            page.add(TurSEResult.builder().fields(fields).build());
        }
        return page;
    }

    /**
     * Rebuilds the hit list in the pipeline's fused order, removing each matched
     * id from {@code byId} (the remainder is appended by the caller as tail) and
     * carrying any ranking explanation back onto the hit.
     */
    private List<TurDslSearchResponse.Hit> reorderByFusion(List<TurSEResult> reordered,
            Map<String, TurDslSearchResponse.Hit> byId, int capacity) {
        List<TurDslSearchResponse.Hit> result = new ArrayList<>(capacity);
        for (TurSEResult res : reordered) {
            Object id = res.getFields() != null ? res.getFields().get(TurSNFieldName.ID) : null;
            TurDslSearchResponse.Hit hit = id != null ? byId.remove(id.toString()) : null;
            if (hit != null) {
                // Carry the objective-ranking explanation the pipeline attached.
                if (res.getRankingExplanation() != null) {
                    hit = withExplanation(hit, res.getRankingExplanation());
                }
                result.add(hit);
            }
        }
        return result;
    }

    private static TurDslSearchResponse.Hit withExplanation(TurDslSearchResponse.Hit hit,
            Map<String, Object> explanation) {
        return new TurDslSearchResponse.Hit(hit.id(), hit.score(), hit.source(), hit.highlight(),
                explanation, hit.fields(), hit.matchedQueries(), hit.version(), hit.seqNo(),
                hit.primaryTerm(), hit.sort());
    }

    // ─────────────────────────── Schema + projection helpers ───────────────────────────

    private List<TurNLFacetField> resolveSchema(TurSNSite site) {
        return turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1).stream()
                .map(this::toField)
                .toList();
    }

    private TurNLFacetField toField(TurSNSiteFieldExt ext) {
        TurSEFieldType type = ext.getType() != null ? ext.getType() : TurSEFieldType.TEXT;
        return new TurNLFacetField(ext.getName(), type, ext.getFacet() == 1, ext.getDescription());
    }

    private String contextBlock(int rank, String title, String url, Map<String, Object> source,
            Map<String, String> fieldDescriptions) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(rank).append("] ").append(title);
        if (StringUtils.hasText(url)) {
            sb.append("\nURL: ").append(url);
        }
        String snippet = firstNonBlank(str(source.get(TurSNFieldName.ABSTRACT)),
                str(source.get(TurSNFieldName.TEXT)));
        if (StringUtils.hasText(snippet)) {
            sb.append("\n").append(truncate(snippet));
        }
        // Append remaining short, non-text fields (facets/attributes) for grounding.
        // T799 / §LIV — label each value by its declared field description when one
        // exists (so the LLM ranks by what a field MEANS, e.g. "overall intelligence
        // index" vs "math sub-score"), keeping the raw key as a parenthetical anchor;
        // fall back to the bare key for fields without a description.
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (isReservedField(key) || entry.getValue() == null) {
                continue;
            }
            String value = str(entry.getValue());
            if (StringUtils.hasText(value) && value.length() <= 80) {
                String description = fieldDescriptions.get(key);
                String label = StringUtils.hasText(description) ? description + " (" + key + ")" : key;
                sb.append("\n").append(label).append(": ").append(value);
            }
        }
        return sb.toString();
    }

    private static boolean isReservedField(String key) {
        return TurSNFieldName.TITLE.equals(key) || TurSNFieldName.URL.equals(key)
                || TurSNFieldName.ABSTRACT.equals(key) || TurSNFieldName.TEXT.equals(key)
                || TurSNFieldName.ID.equals(key);
    }

    /** Renders the grounded query's filter clauses into a short human summary. */
    static String summarize(TurDslQueryRequest request) {
        if (request == null || request.query() == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        collectClauses(request.query(), parts);
        return String.join(", ", parts);
    }

    private static void collectClauses(TurDslQuery query, List<String> parts) {
        switch (query) {
            case TurDslQuery.Bool b -> {
                appendAll(b.filter(), parts);
                appendAll(b.must(), parts);
            }
            case TurDslQuery.Term(String field, Object value) -> parts.add(field + "=" + value);
            case TurDslQuery.Terms(String field, var values) -> parts.add(field + " in " + values);
            case TurDslQuery.Range r -> parts.add(renderRange(r));
            case TurDslQuery.Match m when !TurSNFieldName.DEFAULT.equals(m.field()) ->
                parts.add(m.field() + "~\"" + m.query() + "\"");
            default -> {
                // free-text / unsupported clause — nothing to summarize
            }
        }
    }

    private static void appendAll(List<TurDslQuery> clauses, List<String> parts) {
        if (clauses != null) {
            clauses.forEach(c -> collectClauses(c, parts));
        }
    }

    private static String renderRange(TurDslQuery.Range r) {
        StringBuilder sb = new StringBuilder(r.field());
        if (r.gte() != null) {
            sb.append(" ≥ ").append(r.gte());
        }
        if (r.gt() != null) {
            sb.append(" > ").append(r.gt());
        }
        if (r.lte() != null) {
            sb.append(" ≤ ").append(r.lte());
        }
        if (r.lt() != null) {
            sb.append(" < ").append(r.lt());
        }
        return sb.toString();
    }

    private static String latestUserMessage(List<ChatTurn> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatTurn turn = messages.get(i);
            if (turn != null && !"assistant".equalsIgnoreCase(turn.role())
                    && StringUtils.hasText(turn.content())) {
                return turn.content();
            }
        }
        return null;
    }

    private static Locale safeLocale(String locale) {
        try {
            return StringUtils.hasText(locale) ? LocaleUtils.toLocale(locale) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String truncate(String value) {
        return value.length() > SNIPPET_CHARS ? value.substring(0, SNIPPET_CHARS) + "…" : value;
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    /** Serializes a DSL request for logging; never throws (returns a marker on failure). */
    private static String toJson(TurDslQueryRequest request) {
        try {
            return OBJECT_MAPPER.writeValueAsString(request);
        } catch (RuntimeException e) {
            return "(unserializable: " + e.getMessage() + ")";
        }
    }

    /** {@code "(none)"} for a blank filter summary, so an empty-filter log reads clearly. */
    private static String blankToNone(String summary) {
        return StringUtils.hasText(summary) ? summary : "(none)";
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (StringUtils.hasText(c)) {
                return c;
            }
        }
        return "";
    }

    /** A conversation turn (mirrors the chat APIs' message item). */
    public record ChatTurn(String role, String content) {
    }

    /**
     * The projected outcome of {@link #retrieve}: the cited results, the per-result
     * grounding context blocks fed to the LLM (or the tool's text reply), the total
     * index hit count, a human summary of the applied filters, and whether the site
     * exists at all.
     */
    public record Retrieval(List<TurCatalogCitation> citations, List<String> contextBlocks,
            long totalHits, String groundedSummary, boolean siteFound) {

        static Retrieval notFound() {
            return new Retrieval(List.of(), List.of(), 0L, null, false);
        }
    }
}
