/*
 *
 * Copyright (C) 2016-2025 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.genai;

import static com.viglet.turing.commons.sn.field.TurSNFieldName.ABSTRACT;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.DEFAULT;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.EXACT_MATCH;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.ID;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.IMAGE;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.MODIFICATION_DATE;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.PUBLICATION_DATE;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.SOURCE_APPS;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.TEXT;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.TITLE;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.URL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Component;

import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.sn.TurSNSearchProcess;

import lombok.extern.slf4j.Slf4j;

/**
 * RAG service for Semantic Navigation sites.
 * Handles document indexing into embedding store and RAG-based chat responses.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Slf4j
@Component
public class TurSNGenAi {
    /** T327 — serializes the {@code sources[]} SSE event payload. */
    private static final tools.jackson.databind.ObjectMapper SOURCES_MAPPER =
            new tools.jackson.databind.ObjectMapper();
    public static final String SOURCE_ID = "source_id";
    public static final String SITES = "sites";
    public static final String LOCALE = "locale";

    /** Hard cap on a categorical value's length before it falls into the body. */
    static final int MAX_HEADER_VALUE_LENGTH = 500;

    /** Field names dropped entirely (Solr internals or non-textual URLs). */
    private static final Set<String> RAG_EXCLUDED_FIELDS = Set.of(DEFAULT, EXACT_MATCH, IMAGE);

    /**
     * Field names handled by {@link #buildMetadata} via fixed keys; never
     * appear in the contextual header to avoid duplicating system identity
     * data inside the embedding text.
     */
    private static final Set<String> RAG_SYSTEM_FIELDS = Set.of(
            ID, URL, MODIFICATION_DATE, PUBLICATION_DATE, SOURCE_APPS);

    /**
     * Result of partitioning a document for RAG indexing.
     *
     * @param header         Markdown-like identity block (title + categorical
     *                       fields) propagated to every chunk.
     * @param body           long-form content ({@code abstract} + {@code text})
     *                       that gets chunked.
     * @param customMetadata simple-typed fields (other than system fields)
     *                       captured for ANN facet/filter use; mirrors the
     *                       header so retrieval can {@code fq} on any field.
     */
    record RagFields(String header, String body, Map<String, Object> customMetadata) {
        boolean isEmpty() {
            return (header == null || header.isBlank())
                    && (body == null || body.isBlank());
        }

        static RagFields empty() {
            return new RagFields("", "", Map.of());
        }
    }

    private static final String DEFAULT_BEHAVIOR_PROMPT = """
            You are an assistant that answers questions about this website using only the context provided below.

            How to respond:
            - Use the context as the single source of truth. You may paraphrase, summarize, or combine information from different snippets when they relate to the same topic.
            - If the topic appears in the context only as a passing mention (e.g., a name or word with no explanatory detail), do NOT expand with external knowledge. Instead, answer what the context says — even if it is just "The context mentions X only as <role>, but does not provide further details."
            - If the topic is not present in the context at all, respond exactly with: "I'm sorry, but that information is not available in the website's database."
            - Never use prior knowledge, training data, or outside facts. Even if you know the answer, stay within the context.

            Language: Always respond in English.""";
    private final TurSNSearchProcess turSNSearchProcess;
    private final TurGenAiContextFactory turGenAiContextFactory;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurRagContextBuilder turRagContextBuilder;
    private final TurRagFacetExtractor facetExtractor;
    private final TurAgentChatExecutor agentChatExecutor;
    private final com.viglet.turing.system.TurGlobalSettingsService globalSettingsService;
    private final com.viglet.turing.genai.rag.TurRagReranker ragReranker;

    public TurSNGenAi(TurSNSearchProcess turSNSearchProcess,
            TurGenAiContextFactory turGenAiContextFactory,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurRagContextBuilder turRagContextBuilder,
            TurRagFacetExtractor facetExtractor,
            TurAgentChatExecutor agentChatExecutor,
            com.viglet.turing.system.TurGlobalSettingsService globalSettingsService,
            com.viglet.turing.genai.rag.TurRagReranker ragReranker) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.turGenAiContextFactory = turGenAiContextFactory;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turRagContextBuilder = turRagContextBuilder;
        this.facetExtractor = facetExtractor;
        this.agentChatExecutor = agentChatExecutor;
        this.globalSettingsService = globalSettingsService;
        this.ragReranker = ragReranker;
    }

    public TurChatMessage assistant(TurGenAiContext context, String q) {
        if (!context.isEnabled()) {
            log.warn("RAG chat (single-turn) context disabled — check LLM, embedding model and store configuration");
            return TurChatMessage.builder().text("AI configuration is not enabled").enabled(false).build();
        }
        int tokenCounter = new StringTokenizer(q).countTokens();
        log.debug("RAG chat (single-turn) assistant query='{}' tokenCount={}", q, tokenCounter);
        if (tokenCounter > 1) {
            return getTurChatMessage(context, q);
        } else if (tokenCounter == 1 && !q.contains("*")) {
            String rewritten = "what is %s?".formatted(q);
            log.debug("RAG chat (single-turn) rewriting single-token query to '{}'", rewritten);
            return getTurChatMessage(context, rewritten);
        } else {
            log.debug("RAG chat (single-turn) skipping (wildcard or empty) — no retrieval performed");
            return TurChatMessage.builder().text(null).enabled(true).build();
        }
    }

    /**
     * Conversational RAG: retrieves documents for the latest user message and
     * calls the ChatModel with the full conversation history so follow-up
     * questions preserve context.
     *
     * @since 2026.2.4
     */
    public TurChatMessage assistantConversation(TurGenAiContext context, List<ConversationMessage> history) {
        return assistantConversation(context, history, null);
    }

    /**
     * Same as {@link #assistantConversation(TurGenAiContext, List)} but applies
     * the supplied {@code filters} as a Spring AI {@code Filter.Expression} on
     * the similarity search — restricting the RAG context to chunks whose
     * indexed metadata matches every selected facet.
     *
     * @since 2026.2.4
     */
    /**
     * Streaming conversational variant that delegates LLM execution and tool
     * calling to {@link TurAgentChatExecutor}, while keeping the SN-specific
     * RAG retrieval here. The retrieved chunks are injected into the system
     * prompt before the executor runs, so the agent's tools (native + MCP)
     * are also available to the LLM during the same call.
     *
     * @since 2026.2.4
     */
    public reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> assistantConversationStreaming(
            TurGenAiContext context,
            com.viglet.turing.persistence.model.agent.TurAIAgent agent,
            com.viglet.turing.persistence.model.llm.TurLLMInstance llmInstance,
            List<ConversationMessage> history,
            java.util.Map<String, List<String>> filters,
            String conversationId,
            String flowId) {
        return assistantConversationStreaming(context, agent, llmInstance, history, filters,
                conversationId, flowId, null, Locale.ENGLISH);
    }

    /**
     * Overload carrying a forced A/B variant label (T73 / §VII.8.d) down to
     * the executor so an embedded widget can preview a specific experiment arm
     * via {@code ?_ab_variant=<label>}. A blank value is the production
     * default and behaves identically to the no-variant overload.
     *
     * @since 2026.3.1
     */
    public reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> assistantConversationStreaming(
            TurGenAiContext context,
            com.viglet.turing.persistence.model.agent.TurAIAgent agent,
            com.viglet.turing.persistence.model.llm.TurLLMInstance llmInstance,
            List<ConversationMessage> history,
            java.util.Map<String, List<String>> filters,
            String conversationId,
            String flowId,
            String forcedVariant,
            Locale locale) {
        if (!context.isEnabled()) {
            log.warn("RAG chat streaming context disabled — check agent RAG configuration");
            return reactor.core.publisher.Flux.just(
                    new TurAgentChatExecutor.ChatResponse("assistant", "AI configuration is not enabled"));
        }
        if (history == null || history.isEmpty()) {
            log.debug("RAG chat streaming called with empty history");
            return reactor.core.publisher.Flux.empty();
        }
        String latestUserQuery = history.stream()
                .filter(m -> "user".equals(m.role()))
                .map(ConversationMessage::content)
                .reduce((first, second) -> second)
                .orElse("");
        if (latestUserQuery.isBlank()) {
            return reactor.core.publisher.Flux.empty();
        }

        // T327 — retrieve once, keep the chunks' provenance, then build the
        // stuffed system prompt from the same documents. Previously the public
        // SN path collapsed the chunks to text and discarded the metadata the
        // retriever returned; now it preserves it and emits the same
        // `sources[]` SSE event the agent tool path emits (T292).
        List<Document> relevantDocuments = retrieveDocuments(context, latestUserQuery, filters);

        // T329 — deterministic "not in this site" refusal. When the relevance
        // gate (T328) leaves nothing to ground the answer, short-circuit to the
        // canonical localized refusal WITHOUT an LLM call: cheaper, faster, and
        // impossible to hallucinate around (vs. relying on the model obeying the
        // refusal instruction in DEFAULT_BEHAVIOR_PROMPT). No `sources[]` event —
        // there is no provenance to report.
        if (relevantDocuments.isEmpty()) {
            log.debug("RAG chat streaming: 0 documents passed the relevance gate — "
                    + "returning deterministic refusal (locale={})", locale);
            return reactor.core.publisher.Flux.just(
                    new TurAgentChatExecutor.ChatResponse("assistant", localizedRefusal(locale)));
        }

        String information = relevantDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
        String augmentedSystemPrompt = buildSystemPrompt(context.getSystemPrompt(), information);

        var mapped = history.stream()
                .map(m -> new TurAgentChatExecutor.ChatMessageItem(
                        m.role(), m.content() == null ? "" : m.content()))
                .toList();
        reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> answer =
                agentChatExecutor.execute(agent, llmInstance, mapped, augmentedSystemPrompt,
                        conversationId, flowId, null, forcedVariant);
        reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> grounded =
                appendGroundednessCaveat(answer, context, latestUserQuery, relevantDocuments, locale);
        reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> withSources =
                prependSourcesEvent(grounded, relevantDocuments);
        return appendFollowupsEvent(withSources, context, latestUserQuery, relevantDocuments);
    }

    /**
     * T330 — optional post-generation groundedness (faithfulness) audit. When
     * enabled (Global Settings, default off), buffers the streamed answer and —
     * after it completes — asks a cheap LLM whether every claim is supported by
     * the retrieved chunks. On "unsupported", appends a localized low-confidence
     * caveat as a trailing token (no SDK change: it lands in the same bubble).
     * Fail-open: any error or a "grounded" verdict appends nothing.
     */
    private reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> appendGroundednessCaveat(
            reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> answer,
            TurGenAiContext context, String query, List<Document> documents, Locale locale) {
        if (documents.isEmpty() || !globalSettingsService.isRagSnGroundednessCheckEnabled()) {
            return answer;
        }
        StringBuilder answerBuffer = new StringBuilder();
        reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> buffered = answer
                .doOnNext(chunk -> {
                    String type = chunk.type();
                    if (chunk.content() != null && (type == null || "token".equals(type))) {
                        answerBuffer.append(chunk.content());
                    }
                });
        reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> caveat =
                reactor.core.publisher.Flux.defer(() ->
                        reactor.core.publisher.Mono.fromCallable(() -> {
                            String answerText = answerBuffer.toString();
                            if (answerText.isBlank() || isAnswerGrounded(context, query, documents, answerText)) {
                                return null; // empty → no caveat
                            }
                            return new TurAgentChatExecutor.ChatResponse(
                                    "assistant", "\n\n" + groundednessCaveat(locale), "token");
                        })
                        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                        .flux());
        return reactor.core.publisher.Flux.concat(buffered, caveat);
    }

    /**
     * T330 — single cheap LLM faithfulness check. Returns {@code true} (grounded)
     * on any error so the audit never blocks or falsely flags an answer when the
     * judge call fails (fail-open). The judge replies with a leading GROUNDED /
     * UNSUPPORTED token, parsed leniently.
     */
    private boolean isAnswerGrounded(TurGenAiContext context, String query,
            List<Document> documents, String answerText) {
        try {
            StringBuilder snippets = new StringBuilder();
            int n = Math.min(documents.size(), 6);
            for (int i = 0; i < n; i++) {
                String text = documents.get(i).getText();
                if (text == null) {
                    continue;
                }
                text = text.strip().replaceAll("\\s+", " ");
                snippets.append("- ").append(text.length() > 500
                        ? text.substring(0, 500) + "…" : text).append('\n');
            }
            String prompt = """
                    You are a strict faithfulness judge. Decide whether the ANSWER is fully \
                    supported by the CONTEXT snippets (no claims beyond what the context states).
                    Reply with exactly one word on the first line: GROUNDED or UNSUPPORTED.

                    QUESTION: %s

                    ANSWER: %s

                    CONTEXT:
                    %s""".formatted(query, answerText, snippets);
            String raw = context.getChatModel()
                    .call(new Prompt(prompt))
                    .getResult().getOutput().getText();
            if (raw == null) {
                return true;
            }
            // Lenient: only an explicit UNSUPPORTED verdict flags the answer.
            return !raw.toUpperCase(Locale.ROOT).contains("UNSUPPORTED");
        } catch (RuntimeException e) {
            log.warn("RAG chat groundedness check failed: {}", e.getMessage());
            return true;
        }
    }

    /**
     * T331 — appends grounded follow-up question suggestions as an {@code
     * "options"} chip event after the answer stream (reusing the existing chip
     * contract, so no SDK change). Opt-in via Global Settings (default off);
     * generated from the retrieved chunks + the question with one cheap LLM call
     * that runs <em>after</em> the answer streams (concat ordering). Fail-open:
     * any error or empty result simply emits no event.
     */
    private reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> appendFollowupsEvent(
            reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> stream,
            TurGenAiContext context, String query, List<Document> documents) {
        if (documents.isEmpty() || !globalSettingsService.isRagSnFollowupsEnabled()) {
            return stream;
        }
        reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> followups =
                reactor.core.publisher.Mono.fromCallable(() -> {
                    List<String> questions = generateFollowups(context, query, documents);
                    if (questions.isEmpty()) {
                        return null; // Mono treats null as empty → no event emitted
                    }
                    return new TurAgentChatExecutor.ChatResponse(
                            "assistant", SOURCES_MAPPER.writeValueAsString(questions), "options");
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .flux();
        return reactor.core.publisher.Flux.concat(stream, followups);
    }

    /**
     * T331 — generates 2–3 short follow-up questions grounded in the retrieved
     * chunks. Grounding the suggestions in the same content (not the model's
     * open-ended imagination) keeps them answerable by the same index, avoiding
     * dead-end chips. Returns an empty list on any failure (fail-open).
     */
    private List<String> generateFollowups(TurGenAiContext context, String query,
            List<Document> documents) {
        try {
            StringBuilder snippets = new StringBuilder();
            int n = Math.min(documents.size(), 4);
            for (int i = 0; i < n; i++) {
                String text = documents.get(i).getText();
                if (text == null) {
                    continue;
                }
                text = text.strip().replaceAll("\\s+", " ");
                snippets.append("- ").append(text.length() > 400
                        ? text.substring(0, 400) + "…" : text).append('\n');
            }
            String prompt = """
                    Based ONLY on the context snippets below and the user's question, \
                    suggest 2-3 short follow-up questions the user might naturally ask next \
                    that can be answered from the SAME content. \
                    Keep each under 12 words. Return ONE question per line, no numbering, no preamble.

                    Question: %s

                    Context snippets:
                    %s""".formatted(query, snippets);
            String raw = context.getChatModel()
                    .call(new Prompt(prompt))
                    .getResult().getOutput().getText();
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            List<String> out = new ArrayList<>(3);
            for (String line : raw.split("\\r?\\n")) {
                String q = line.strip()
                        // strip leading bullets / numbering ("1.", "-", "*", "•")
                        .replaceFirst("^\\s*(?:[-*•]|\\d+[.)])\\s*", "")
                        .strip();
                if (!q.isBlank()) {
                    out.add(q);
                }
                if (out.size() == 3) {
                    break;
                }
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("RAG chat follow-up generation failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * T327 — emits a {@code "sources"} SSE event (same contract as T292) carrying
     * the per-chunk provenance, ahead of the assistant's answer stream. The
     * order is irrelevant to the client: both SDKs collect the {@code sources[]}
     * regardless of arrival position and attach it to the assistant message on
     * stream completion. Returns the answer stream unchanged when there is no
     * provenance to report or it can't be serialized.
     */
    private reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> prependSourcesEvent(
            reactor.core.publisher.Flux<TurAgentChatExecutor.ChatResponse> answer,
            List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return answer;
        }
        List<com.viglet.turing.genai.rag.TurRagSource> sources = documents.stream()
                .map(com.viglet.turing.genai.rag.TurRagSource::fromDocument)
                .toList();
        try {
            String json = SOURCES_MAPPER.writeValueAsString(sources);
            return reactor.core.publisher.Flux.concat(
                    reactor.core.publisher.Flux.just(
                            new TurAgentChatExecutor.ChatResponse("assistant", json, "sources")),
                    answer);
        } catch (tools.jackson.core.JacksonException e) {
            log.warn("RAG chat could not serialize {} source(s): {}", sources.size(), e.getMessage());
            return answer;
        }
    }

    /**
     * Shared SN RAG retrieval: auto-extracts filters when none are given, picks
     * {@code topK}/threshold based on whether filtering narrows the universe,
     * runs the similarity search, then (T328) applies the config-driven
     * relevance gate on the filtered path and an optional LLM reranker before
     * returning the chunks. Used by both the streaming tool-calling flow and
     * the legacy single-shot flow so the gate/rerank behavior is identical.
     *
     * <p>Returns the retrieved {@link Document}s with their metadata intact —
     * the provenance the {@code sources[]} SSE event (T327) needs — instead of
     * collapsing them to text at the call site.
     *
     * @since 2026.3.1 (T328)
     */
    private List<Document> retrieveDocuments(TurGenAiContext context,
            String latestUserQuery,
            java.util.Map<String, List<String>> filters) {
        java.util.Map<String, List<String>> effectiveFilters = filters;
        boolean autoExtracted = false;
        if (effectiveFilters == null || effectiveFilters.isEmpty()) {
            try {
                java.util.Map<String, List<String>> available = facetExtractor
                        .getAvailableFacets(context.getRagInfrastructure());
                if (!available.isEmpty()) {
                    java.util.Map<String, List<String>> extracted = facetExtractor
                            .extractFilters(context.getChatModel(), latestUserQuery, available);
                    if (!extracted.isEmpty()) {
                        effectiveFilters = extracted;
                        autoExtracted = true;
                        log.debug("RAG chat auto-extracted filters from query '{}': {}",
                                latestUserQuery, extracted);
                    }
                }
            } catch (Exception e) {
                log.warn("RAG chat auto-extraction failed, falling back to plain similarity: {}",
                        e.getMessage());
            }
        }
        boolean hasFilters = effectiveFilters != null && !effectiveFilters.isEmpty();
        int topK = hasFilters ? 50 : 10;
        // T328 — the filtered path historically used a hard-coded 0.0 floor,
        // stuffing every filter-matching hit regardless of semantic score (the
        // dominant hallucination driver). Now config-driven (default 0.0 keeps
        // legacy behavior). The unfiltered path keeps its own 0.4 floor.
        double similarityThreshold = hasFilters
                ? globalSettingsService.getRagSnSimilarityThreshold()
                : 0.4;
        org.springframework.ai.vectorstore.filter.Filter.Expression filterExpr =
                TurRagFilters.buildFilterExpression(effectiveFilters);
        SearchRequest.Builder reqBuilder = SearchRequest.builder()
                .query(latestUserQuery)
                .topK(topK)
                .similarityThreshold(similarityThreshold);
        if (filterExpr != null) {
            reqBuilder.filterExpression(filterExpr);
        }
        SearchRequest embeddingSearchRequest = reqBuilder.build();
        log.debug("RAG chat similaritySearch: topK={}, threshold={}, filters={}{}, query='{}'",
                topK, similarityThreshold, effectiveFilters,
                autoExtracted ? " (auto-extracted)" : "", latestUserQuery);
        List<Document> searchResult = context.getVectorStore().similaritySearch(embeddingSearchRequest);
        List<Document> relevantDocuments = searchResult == null ? Collections.emptyList() : searchResult;
        log.debug("RAG chat similaritySearch returned {} document(s)", relevantDocuments.size());

        // T328 — optional LLM reranker: narrow the candidate pool to the
        // highest-precision top-k before stuffing. Opt-in (default off);
        // fail-open (returns retrieval order on any error).
        if (globalSettingsService.isRagSnRerankEnabled() && relevantDocuments.size() > 1) {
            int keep = Math.min(globalSettingsService.getRagSnRerankTopN(), relevantDocuments.size());
            List<Document> reranked = ragReranker.rerank(
                    context.getChatModel(), latestUserQuery, relevantDocuments, keep);
            log.debug("RAG chat reranked {} → {} document(s)",
                    relevantDocuments.size(), reranked.size());
            relevantDocuments = reranked;
        }
        return relevantDocuments;
    }

    public TurChatMessage assistantConversation(TurGenAiContext context, List<ConversationMessage> history,
            java.util.Map<String, List<String>> filters) {
        if (!context.isEnabled()) {
            log.warn("RAG chat context disabled — check LLM, embedding model and store configuration");
            return TurChatMessage.builder().text("AI configuration is not enabled").enabled(false).build();
        }
        if (history == null || history.isEmpty()) {
            log.debug("RAG chat called with empty history — nothing to answer");
            return TurChatMessage.builder().text(null).enabled(true).build();
        }

        String latestUserQuery = history.stream()
                .filter(m -> "user".equals(m.role()))
                .map(ConversationMessage::content)
                .reduce((first, second) -> second)
                .orElse("");
        if (latestUserQuery.isBlank()) {
            log.debug("RAG chat latest user message is blank — nothing to retrieve");
            return TurChatMessage.builder().text(null).enabled(true).build();
        }

        // Shared retrieval (T328): auto-extracts filters, applies the
        // config-driven relevance gate on the filtered path, and runs the
        // optional reranker. Returns chunks with metadata intact.
        List<Document> relevantDocuments = retrieveDocuments(context, latestUserQuery, filters);
        String vectorStoreType = context.getVectorStore().getClass().getSimpleName();
        if (relevantDocuments.isEmpty()) {
            log.warn("RAG chat retrieved 0 documents — check that indexing populated the store '{}' with the collection matching this site/locale",
                    vectorStoreType);
        } else if (log.isInfoEnabled()) {
            for (int i = 0; i < relevantDocuments.size(); i++) {
                Document d = relevantDocuments.get(i);
                String text = d.getText() == null ? "" : d.getText();
                log.debug("  RAG chat doc[{}] metadata={} text=<<<\n{}\n>>>", i, d.getMetadata(), text);
            }
        }
        String information = relevantDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        log.debug("RAG chat raw system prompt template=<<<\n{}\n>>>", context.getSystemPrompt());
        String systemContent = buildSystemPrompt(context.getSystemPrompt(), information);
        log.debug("RAG chat final system prompt sent to LLM=<<<\n{}\n>>>", systemContent);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemContent));
        for (ConversationMessage m : history) {
            if ("assistant".equals(m.role())) {
                messages.add(new AssistantMessage(m.content() == null ? "" : m.content()));
            } else {
                messages.add(new UserMessage(m.content() == null ? "" : m.content()));
            }
        }

        Prompt prompt = new Prompt(messages);
        if (log.isInfoEnabled()) {
            StringBuilder dump = new StringBuilder();
            for (Message m : messages) {
                dump.append("\n---- ").append(m.getMessageType()).append(" ----\n")
                        .append(m.getText() == null ? "" : m.getText());
            }
            log.debug("RAG chat full prompt (system + turns) sent to LLM=<<<{}\n>>>", dump);
        }
        String answer = context.getChatModel().call(prompt).getResult().getOutput().getText();
        log.debug("RAG chat LLM answer=<<<\n{}\n>>>", answer);
        return TurChatMessage.builder().text(answer).enabled(true).build();
    }

    /**
     * Composes the system message for RAG chat.
     * <p>
     * The custom prompt is treated as pure behavior guidance. The retrieved RAG
     * context is appended automatically; the user question is carried by the
     * {@code UserMessage} turn, not injected into the system prompt.
     */
    private String buildSystemPrompt(String configuredPrompt, String information) {
        String behavior = (configuredPrompt == null || configuredPrompt.isBlank())
                ? DEFAULT_BEHAVIOR_PROMPT
                : configuredPrompt;
        if (!information.isBlank()) {
            behavior = behavior + "\n\n---\nContext from the website:\n" + information + "\n---";
        }
        return behavior;
    }

    /**
     * T329 — canonical "not in this site" refusal, keyed by language. The
     * English entry matches the refusal sentence baked into
     * {@link #DEFAULT_BEHAVIOR_PROMPT} so the deterministic short-circuit reads
     * identically to what the model would have produced. Unknown languages fall
     * back to English. Self-contained (no backend i18n bundle exists) and scoped
     * to the configset locales (en/pt/es/ca).
     */
    private static final java.util.Map<String, String> REFUSAL_BY_LANGUAGE = java.util.Map.of(
            "en", "I'm sorry, but that information is not available in the website's database.",
            "pt", "Desculpe, mas essa informação não está disponível na base de dados do site.",
            "es", "Lo siento, pero esa información no está disponible en la base de datos del sitio web.",
            "ca", "Ho sento, però aquesta informació no està disponible a la base de dades del lloc web.");

    private static String localizedRefusal(Locale locale) {
        return localized(REFUSAL_BY_LANGUAGE, locale);
    }

    /**
     * T330 — localized low-confidence caveat appended when the groundedness
     * audit flags an answer as not fully supported by the retrieved content.
     * Scoped to the configset locales (en/pt/es/ca), English fallback.
     */
    private static final java.util.Map<String, String> GROUNDEDNESS_CAVEAT_BY_LANGUAGE = java.util.Map.of(
            "en", "_Note: this answer may not be fully supported by the site's content — please verify._",
            "pt", "_Observação: esta resposta pode não estar totalmente apoiada no conteúdo do site — verifique._",
            "es", "_Nota: es posible que esta respuesta no esté totalmente respaldada por el contenido del sitio — verifíquela._",
            "ca", "_Nota: és possible que aquesta resposta no estigui del tot recolzada pel contingut del lloc — verifiqueu-la._");

    private static String groundednessCaveat(Locale locale) {
        return localized(GROUNDEDNESS_CAVEAT_BY_LANGUAGE, locale);
    }

    private static String localized(java.util.Map<String, String> byLanguage, Locale locale) {
        String language = locale == null ? "en" : locale.getLanguage();
        if (language == null || language.isBlank()) {
            language = "en";
        }
        return byLanguage.getOrDefault(language.toLowerCase(Locale.ROOT), byLanguage.get("en"));
    }

    public record ConversationMessage(String role, String content) {
    }

    private TurChatMessage getTurChatMessage(TurGenAiContext context, String q) {
        int topK = 10;
        double similarityThreshold = 0.4;
        SearchRequest embeddingSearchRequest = SearchRequest.builder()
                .query(q)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
        String vectorStoreType = context.getVectorStore().getClass().getSimpleName();
        log.debug("RAG chat (single-turn) similaritySearch: store='{}', topK={}, threshold={}, query='{}'",
                vectorStoreType, topK, similarityThreshold, q);
        List<Document> searchResult = context.getVectorStore().similaritySearch(embeddingSearchRequest);
        List<Document> relevantDocuments = searchResult == null ? Collections.emptyList() : searchResult;
        log.debug("RAG chat (single-turn) similaritySearch returned {} document(s)", relevantDocuments.size());
        if (relevantDocuments.isEmpty()) {
            log.warn("RAG chat (single-turn) retrieved 0 documents — check that indexing populated the store '{}' with the collection matching this site/locale",
                    vectorStoreType);
        } else if (log.isInfoEnabled()) {
            for (int i = 0; i < relevantDocuments.size(); i++) {
                Document d = relevantDocuments.get(i);
                String text = d.getText() == null ? "" : d.getText();
                log.debug("  RAG chat (single-turn) doc[{}] metadata={} text=<<<\n{}\n>>>", i, d.getMetadata(), text);
            }
        }

        String information = relevantDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
        log.debug("RAG chat (single-turn) raw system prompt template=<<<\n{}\n>>>", context.getSystemPrompt());
        String systemContent = buildSystemPrompt(context.getSystemPrompt(), information);
        log.debug("RAG chat (single-turn) final system prompt sent to LLM=<<<\n{}\n>>>", systemContent);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemContent));
        messages.add(new UserMessage(q));
        Prompt prompt = new Prompt(messages);
        if (log.isInfoEnabled()) {
            StringBuilder dump = new StringBuilder();
            for (Message m : messages) {
                dump.append("\n---- ").append(m.getMessageType()).append(" ----\n")
                        .append(m.getText() == null ? "" : m.getText());
            }
            log.debug("RAG chat (single-turn) full prompt (system + turns) sent to LLM=<<<{}\n>>>", dump);
        }
        String answer = context.getChatModel().call(prompt).getResult().getOutput().getText();
        log.debug("RAG chat (single-turn) LLM answer=<<<\n{}\n>>>", answer);
        return TurChatMessage.builder()
                .text(answer)
                .enabled(true)
                .build();
    }

    /**
     * Index a single document coming from the content import/exchange flow, where the
     * document is a raw map of attributes (instead of a {@link TurSNJobItem}).
     *
     * @since 2026.2.4
     */
    public void addDocument(TurSNSite turSNSite, Locale locale, Map<String, Object> doc) {
        if (turSNSite == null || doc == null) {
            return;
        }
        Object sourceId = doc.get(ID);
        if (turSNSite.getTurSNSiteGenAi() == null) {
            log.debug("RAG skipped for Object ID '{}' of SN Site '{}': no GenAI binding",
                    sourceId, turSNSite.getName());
            return;
        }
        String collectionName = resolveCollectionName(turSNSite, locale);
        log.debug("RAG building context for Object ID '{}' of SN Site '{}' (collection='{}')",
                sourceId, turSNSite.getName(), collectionName);
        TurGenAiContext context = turGenAiContextFactory.build(
                turSNSite.getTurSNSiteGenAi(), collectionName);
        if (!context.isEnabled()) {
            log.warn("RAG context disabled for Object ID '{}' of SN Site '{}': agent not configured for RAG",
                    sourceId, turSNSite.getName());
            return;
        }
        RagFields fields = partitionFields(doc);
        if (fields.isEmpty()) {
            log.warn("RAG document for Object ID '{}' of SN Site '{}' has no indexable text (title/abstract/text attributes are empty)",
                    sourceId, turSNSite.getName());
        }
        Map<String, Object> metadata = buildMetadata(turSNSite, locale, doc, fields);
        addDocument(context, fields.header(), fields.body(), metadata);
    }

    /**
     * Indexes a document with a {@link TurGenAiContext} that was already
     * built by the caller — skips the per-doc {@code resolveCollectionName}
     * and {@code turGenAiContextFactory.build} round-trips. Used by the
     * parallel reindex flow which shares a single context across all
     * documents of a locale, since the context is locale-bound but
     * doc-independent.
     *
     * <p>Must only be called when {@code context.isEnabled()} is already
     * verified (the public {@link #addDocument(TurSNSite, Locale, Map)}
     * still does the validation when no pre-built context is available).</p>
     *
     * @since 2026.2.4
     */
    public void addDocumentToContext(TurSNSite turSNSite, Locale locale,
            TurGenAiContext context, Map<String, Object> doc) {
        if (turSNSite == null || context == null || !context.isEnabled() || doc == null) {
            return;
        }
        Object sourceId = doc.get(ID);
        RagFields fields = partitionFields(doc);
        if (fields.isEmpty()) {
            log.warn("RAG document for Object ID '{}' of SN Site '{}' has no indexable text (title/abstract/text attributes are empty)",
                    sourceId, turSNSite.getName());
        }
        Map<String, Object> metadata = buildMetadata(turSNSite, locale, doc, fields);
        addDocument(context, fields.header(), fields.body(), metadata);
    }

    /**
     * Partitions a document's attributes into:
     * <ul>
     *   <li><b>Header</b> — categorical/short fields (title + custom fields)
     *       formatted as a Markdown-like block ({@code "# Title\nField: value\n..."})
     *       that is propagated to every chunk so each chunk is self-contained
     *       and the embedding sees the document's full identity regardless of
     *       which slice is retrieved.</li>
     *   <li><b>Body</b> — long-form content ({@code abstract} + {@code text})
     *       that gets chunked.</li>
     *   <li><b>customMetadata</b> — every simple-typed field captured for
     *       filter/facet use, mirroring what the header sees so ANN queries
     *       can {@code fq} on any field without re-indexing.</li>
     * </ul>
     *
     * <p>System fields ({@link #ID}, {@link #URL}, {@link #MODIFICATION_DATE},
     * {@link #PUBLICATION_DATE}, {@link #SOURCE_APPS}) are excluded from the
     * header/customMetadata path because {@link #buildMetadata} adds them via
     * fixed keys. {@link #IMAGE} and Solr internals ({@code _text_},
     * {@code exact_match}) are dropped entirely.</p>
     */
    RagFields partitionFields(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return RagFields.empty();
        }
        StringBuilder header = new StringBuilder();
        StringBuilder body = new StringBuilder();
        Map<String, Object> customMetadata = new LinkedHashMap<>();

        Object titleValue = attributes.get(TITLE);
        if (titleValue != null) {
            String title = TurRagUtils.stripHtml(titleValue.toString()).trim();
            if (!title.isEmpty()) {
                header.append("# ").append(title).append("\n");
                customMetadata.put(TITLE, titleValue);
            }
        }

        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null || RAG_EXCLUDED_FIELDS.contains(key) || RAG_SYSTEM_FIELDS.contains(key)
                    || TITLE.equals(key) || ABSTRACT.equals(key) || TEXT.equals(key)) {
                continue;
            }
            if (!isSimpleType(value)) {
                continue;
            }
            String formatted = formatHeaderValue(value);
            if (formatted.isBlank()) {
                continue;
            }
            // Long values fall through to the body so the header stays compact.
            if (formatted.length() > MAX_HEADER_VALUE_LENGTH) {
                body.append(key).append(": ").append(formatted).append("\n\n");
                continue;
            }
            header.append(key).append(": ").append(formatted).append("\n");
            customMetadata.put(key, value);
        }

        Object abstractValue = attributes.get(ABSTRACT);
        if (abstractValue != null) {
            String stripped = TurRagUtils.stripHtml(abstractValue.toString()).trim();
            if (!stripped.isEmpty()) {
                body.append(stripped).append("\n\n");
            }
        }
        Object textValue = attributes.get(TEXT);
        if (textValue != null) {
            String stripped = TurRagUtils.stripHtml(textValue.toString()).trim();
            if (!stripped.isEmpty()) {
                body.append(stripped).append("\n\n");
            }
        }

        return new RagFields(
                header.toString().stripTrailing(),
                body.toString().stripTrailing(),
                customMetadata);
    }

    private static String formatHeaderValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(Object::toString)
                    .map(TurRagUtils::stripHtml)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(", "));
        }
        return TurRagUtils.stripHtml(value.toString()).trim();
    }

    private static boolean isSimpleType(Object value) {
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return true;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().allMatch(v -> v == null
                    || v instanceof CharSequence
                    || v instanceof Number
                    || v instanceof Boolean);
        }
        return false;
    }

    private Map<String, Object> buildMetadata(TurSNSite turSNSite, Locale locale,
            Map<String, Object> doc, RagFields fields) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(SOURCE_ID, doc.get(ID));
        if (locale != null) {
            metadata.put(LOCALE, locale.toString());
        }
        if (doc.containsKey(SOURCE_APPS)) {
            metadata.put(SOURCE_APPS, doc.get(SOURCE_APPS));
        }
        metadata.put(SITES, turSNSite.getName());
        if (doc.containsKey(MODIFICATION_DATE)) {
            metadata.put(MODIFICATION_DATE, doc.get(MODIFICATION_DATE));
        }
        if (doc.containsKey(PUBLICATION_DATE)) {
            metadata.put(PUBLICATION_DATE, doc.get(PUBLICATION_DATE));
        }
        if (doc.containsKey(URL)) {
            metadata.put(URL, doc.get(URL));
        }
        // Categorical/short fields collected by partitionFields — these power
        // ANN facets and filter queries (every key indexed as meta_<key> in Lucene).
        for (Map.Entry<String, Object> entry : fields.customMetadata().entrySet()) {
            metadata.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return metadata;
    }

    private String resolveCollectionName(TurSNSite turSNSite, Locale locale) {
        if (locale == null) {
            return null;
        }
        TurSNSiteLocale siteLocale = turSNSiteLocaleRepository
                .findByTurSNSiteAndLanguage(turSNSite, locale);
        return siteLocale != null ? siteLocale.getCore() : null;
    }

    public void addDocuments(TurSNJobItems turSNJobItems) {
        log.debug("RAG addDocuments called with {} document(s)", turSNJobItems.getTuringDocuments().size());
        turSNJobItems.getTuringDocuments().forEach(jobItem -> jobItem.getSiteNames()
                .forEach(siteName -> turSNSearchProcess.getSNSite(siteName).ifPresentOrElse(turSNSite -> {
                    if (turSNSite.getTurSNSiteGenAi() == null) {
                        log.debug("RAG skipped for Object ID '{}' of SN Site '{}': no GenAI binding",
                                jobItem.getId(), siteName);
                        return;
                    }
                    String collectionName = resolveCollectionName(turSNSite, jobItem.getLocale());
                    log.debug("RAG building context for Object ID '{}' of SN Site '{}' (collection='{}')",
                            jobItem.getId(), siteName, collectionName);
                    TurGenAiContext context = turGenAiContextFactory.build(
                            turSNSite.getTurSNSiteGenAi(), collectionName);
                    if (!context.isEnabled()) {
                        log.warn("RAG context disabled for Object ID '{}' of SN Site '{}': check LLM, embedding model and store configuration",
                                jobItem.getId(), siteName);
                        return;
                    }
                    RagFields fields = partitionFields(jobItem.getAttributes());
                    if (fields.isEmpty()) {
                        log.warn("RAG document for Object ID '{}' of SN Site '{}' has no indexable text (title/abstract/text attributes are empty)",
                                jobItem.getId(), siteName);
                    }
                    addDocument(context, fields.header(), fields.body(), buildMetadataFromJob(jobItem, fields));
                }, () -> log.warn("RAG skipped for Object ID '{}': SN Site '{}' not resolved by search process",
                        jobItem.getId(), siteName))));
    }

    private void addDocument(TurGenAiContext context, String header, String body, Map<String, Object> metadata) {
        Object sourceId = metadata.get(SOURCE_ID);
        String idPrefix = sourceId == null ? null : sourceId.toString();
        List<Document> documents = TurRagUtils.createDocumentsWithHeader(header, body, metadata, idPrefix);
        String vectorStoreType = context.getVectorStore().getClass().getSimpleName();
        if (log.isDebugEnabled()) {
            log.debug("RAG indexing metadata for Object ID '{}': {}", sourceId, metadata);
            for (int i = 0; i < documents.size(); i++) {
                Document chunk = documents.get(i);
                log.debug("  RAG indexing chunk[{}/{}] Object ID '{}' text=<<<\n{}\n>>>",
                        i + 1, documents.size(), sourceId,
                        chunk.getText() == null ? "" : chunk.getText());
            }
        }
        turRagContextBuilder.reindexByMetadata(context.getRagInfrastructure(), documents,
                SOURCE_ID, sourceId == null ? null : sourceId.toString());
        // Logged AFTER reindexByMetadata so the INFO line confirms the
        // documents actually landed in the vector store. If reindexByMetadata
        // throws, the caller sees a WARN/ERROR instead of a misleading
        // "indexing" log followed by silent failure.
        log.debug("RAG indexed Object ID '{}' ({} chunk{}) into vector store '{}'",
                sourceId, documents.size(), documents.size() == 1 ? "" : "s", vectorStoreType);
    }

    @NotNull
    private Map<String, Object> buildMetadataFromJob(TurSNJobItem jobItem, RagFields fields) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(SOURCE_ID, jobItem.getId());
        if (jobItem.getLocale() != null) {
            metadata.put(LOCALE, jobItem.getLocale().toString());
        }
        metadata.put(SOURCE_APPS, jobItem.getProviderName());
        metadata.put(SITES, jobItem.getSiteNames().getFirst());
        if (jobItem.getAttributes().containsKey(MODIFICATION_DATE)) {
            metadata.put(MODIFICATION_DATE, jobItem.getAttributes().get(MODIFICATION_DATE));
        }
        if (jobItem.getAttributes().containsKey(PUBLICATION_DATE)) {
            metadata.put(PUBLICATION_DATE, jobItem.getAttributes().get(PUBLICATION_DATE));
        }
        if (jobItem.getAttributes().containsKey(URL)) {
            metadata.put(URL, jobItem.getAttributes().get(URL));
        }
        // Categorical/short fields collected by partitionFields — these power
        // ANN facets and filter queries (every key indexed as meta_<key> in Lucene).
        for (Map.Entry<String, Object> entry : fields.customMetadata().entrySet()) {
            metadata.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return metadata;
    }

}
