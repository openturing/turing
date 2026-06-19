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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser.ParseRequest;
import com.viglet.turing.sn.dsl.eval.TurNLFacetParser.TurNLFacetParseException;
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
 *   <li><b>NL→facet (T385)</b> — the user's prose is parsed into a grounded
 *       {@link TurDslQueryRequest} over the site's <em>declared</em> field schema
 *       by {@link TurNLFacetParser} (it may only reference declared fields; it
 *       omits what it cannot ground).</li>
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

    /** Maximum number of catalog results used as grounding context / citations. */
    private static final int MAX_RESULTS = 8;
    /** Cap on the per-result snippet length fed to the answer LLM. */
    private static final int SNIPPET_CHARS = 320;

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final TurNLFacetParser parser;
    private final TurDslSearchService dslSearchService;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNHybridRankingService hybridRankingService;
    private final TurDefaultChatModelResolver chatModelResolver;

    public TurCatalogCopilotService(TurNLFacetParser parser,
            TurDslSearchService dslSearchService,
            TurSNSiteRepository turSNSiteRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNHybridRankingService hybridRankingService,
            TurDefaultChatModelResolver chatModelResolver) {
        this.parser = parser;
        this.dslSearchService = dslSearchService;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.hybridRankingService = hybridRankingService;
        this.chatModelResolver = chatModelResolver;
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

        Retrieval retrieval = retrieve(siteName, locale, latestUser);
        if (!retrieval.siteFound()) {
            return TurCatalogCopilotResult.error("Catalog site not found: " + siteName);
        }

        try {
            String answer = callLlm(chatModelOpt.get(), siteName, retrieval, messages);
            return TurCatalogCopilotResult.ok(answer, retrieval.citations(),
                    retrieval.groundedSummary(), retrieval.totalHits());
        } catch (RuntimeException e) {
            log.warn("[CatalogCopilot] answer LLM call failed for site '{}': {} — returning citations only",
                    siteName, e.getMessage());
            return TurCatalogCopilotResult.degraded(retrieval.citations(),
                    retrieval.groundedSummary(), retrieval.totalHits(),
                    "Answer generation failed: " + e.getMessage());
        }
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
                from the search index and are ordered by objective relevance only.

                HARD RULES:
                - Use ONLY the information in CATALOG RESULTS. Never invent items, \
                  prices, attributes, or links that are not present below.
                - Cite every claim with the bracketed number of the result it comes \
                  from, e.g. [1], [2]. Cite multiple when relevant ([1][3]).
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

        TurDslQueryRequest request = parseOrFallback(siteName, locale, query, schema);
        Optional<TurDslSearchResponse> responseOpt =
                dslSearchService.search(siteName, searchLocale, request);
        if (responseOpt.isEmpty() || responseOpt.get().hits() == null
                || responseOpt.get().hits().hits() == null) {
            return new Retrieval(List.of(), List.of(), 0L, summarize(request), true);
        }

        TurDslSearchResponse response = responseOpt.get();
        List<TurDslSearchResponse.Hit> hits = response.hits().hits();
        long totalHits = response.hits().total() != null ? response.hits().total().value() : hits.size();

        List<TurDslSearchResponse.Hit> ordered = applyHybridRanking(site, searchLocale, query, hits);

        List<TurCatalogCitation> citations = new ArrayList<>();
        List<String> contextBlocks = new ArrayList<>();
        int limit = Math.min(MAX_RESULTS, ordered.size());
        for (int i = 0; i < limit; i++) {
            TurDslSearchResponse.Hit hit = ordered.get(i);
            int rank = i + 1;
            Map<String, Object> source = hit.source() != null ? hit.source() : Map.of();
            String title = firstNonBlank(str(source.get(TurSNFieldName.TITLE)), hit.id());
            String url = str(source.get(TurSNFieldName.URL));
            citations.add(new TurCatalogCitation(rank, hit.id(), title,
                    StringUtils.hasText(url) ? url : null, hit.score(),
                    hit.explanation()));
            contextBlocks.add(contextBlock(rank, title, url, source));
        }
        return new Retrieval(citations, contextBlocks, totalHits, summarize(request), true);
    }

    private TurDslQueryRequest parseOrFallback(String siteName, String locale, String query,
            List<TurNLFacetField> schema) {
        if (parser.isAvailable() && !schema.isEmpty()) {
            try {
                return parser.parse(new ParseRequest(siteName, locale, query, schema));
            } catch (TurNLFacetParseException e) {
                log.warn("[CatalogCopilot] NL→facet parse failed for '{}': {} — falling back to free-text",
                        siteName, e.getMessage());
            }
        }
        return freeTextRequest(query);
    }

    /** A plain full-text query body used when the parser is unavailable or fails. */
    private TurDslQueryRequest freeTextRequest(String query) {
        String safe = query == null ? "" : query.replace("\"", " ").replace("\\", " ");
        String body = "{\"query\":{\"match\":{\"_text_\":\"%s\"}},\"size\":%d}"
                .formatted(safe, MAX_RESULTS);
        return OBJECT_MAPPER.readValue(body, TurDslQueryRequest.class);
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

    private String contextBlock(int rank, String title, String url, Map<String, Object> source) {
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
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (isReservedField(key) || entry.getValue() == null) {
                continue;
            }
            String value = str(entry.getValue());
            if (StringUtils.hasText(value) && value.length() <= 80) {
                sb.append("\n").append(key).append(": ").append(value);
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
            case TurDslQuery.Term t -> parts.add(t.field() + "=" + t.value());
            case TurDslQuery.Terms t -> parts.add(t.field() + " in " + t.values());
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
