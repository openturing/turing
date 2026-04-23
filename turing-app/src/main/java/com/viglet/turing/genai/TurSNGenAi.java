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
import static com.viglet.turing.commons.sn.field.TurSNFieldName.ID;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.MODIFICATION_DATE;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.PUBLICATION_DATE;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.SOURCE_APPS;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.TEXT;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.TITLE;
import static com.viglet.turing.commons.sn.field.TurSNFieldName.URL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    public static final String SOURCE_ID = "source_id";
    public static final String SITES = "sites";
    public static final String LOCALE = "locale";
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

    public TurSNGenAi(TurSNSearchProcess turSNSearchProcess,
            TurGenAiContextFactory turGenAiContextFactory,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurRagContextBuilder turRagContextBuilder) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.turGenAiContextFactory = turGenAiContextFactory;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turRagContextBuilder = turRagContextBuilder;
    }

    public TurChatMessage assistant(TurGenAiContext context, String q) {
        if (!context.isEnabled()) {
            log.warn("RAG chat (single-turn) context disabled — check LLM, embedding model and store configuration");
            return TurChatMessage.builder().text("AI configuration is not enabled").enabled(false).build();
        }
        int tokenCounter = new StringTokenizer(q).countTokens();
        log.info("RAG chat (single-turn) assistant query='{}' tokenCount={}", q, tokenCounter);
        if (tokenCounter > 1) {
            return getTurChatMessage(context, q);
        } else if (tokenCounter == 1 && !q.contains("*")) {
            String rewritten = "what is %s?".formatted(q);
            log.info("RAG chat (single-turn) rewriting single-token query to '{}'", rewritten);
            return getTurChatMessage(context, rewritten);
        } else {
            log.info("RAG chat (single-turn) skipping (wildcard or empty) — no retrieval performed");
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
        if (!context.isEnabled()) {
            log.warn("RAG chat context disabled — check LLM, embedding model and store configuration");
            return TurChatMessage.builder().text("AI configuration is not enabled").enabled(false).build();
        }
        if (history == null || history.isEmpty()) {
            log.info("RAG chat called with empty history — nothing to answer");
            return TurChatMessage.builder().text(null).enabled(true).build();
        }

        String latestUserQuery = history.stream()
                .filter(m -> "user".equals(m.role()))
                .map(ConversationMessage::content)
                .reduce((first, second) -> second)
                .orElse("");
        if (latestUserQuery.isBlank()) {
            log.info("RAG chat latest user message is blank — nothing to retrieve");
            return TurChatMessage.builder().text(null).enabled(true).build();
        }

        int topK = 10;
        double similarityThreshold = 0.4;
        SearchRequest embeddingSearchRequest = SearchRequest.builder()
                .query(latestUserQuery)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
        String vectorStoreType = context.getVectorStore().getClass().getSimpleName();
        log.info("RAG chat similaritySearch: store='{}', topK={}, threshold={}, query='{}'",
                vectorStoreType, topK, similarityThreshold, latestUserQuery);
        List<Document> searchResult = context.getVectorStore().similaritySearch(embeddingSearchRequest);
        List<Document> relevantDocuments = searchResult == null ? Collections.emptyList() : searchResult;
        log.info("RAG chat similaritySearch returned {} document(s)", relevantDocuments.size());
        if (relevantDocuments.isEmpty()) {
            log.warn("RAG chat retrieved 0 documents — check that indexing populated the store '{}' with the collection matching this site/locale",
                    vectorStoreType);
        } else if (log.isInfoEnabled()) {
            for (int i = 0; i < relevantDocuments.size(); i++) {
                Document d = relevantDocuments.get(i);
                String text = d.getText() == null ? "" : d.getText();
                log.info("  RAG chat doc[{}] metadata={} text=<<<\n{}\n>>>", i, d.getMetadata(), text);
            }
        }
        String information = relevantDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        log.info("RAG chat raw system prompt template=<<<\n{}\n>>>", context.getSystemPrompt());
        String systemContent = buildSystemPrompt(context.getSystemPrompt(), information);
        log.info("RAG chat final system prompt sent to LLM=<<<\n{}\n>>>", systemContent);

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
            log.info("RAG chat full prompt (system + turns) sent to LLM=<<<{}\n>>>", dump);
        }
        String answer = context.getChatModel().call(prompt).getResult().getOutput().getText();
        log.info("RAG chat LLM answer=<<<\n{}\n>>>", answer);
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
        log.info("RAG chat (single-turn) similaritySearch: store='{}', topK={}, threshold={}, query='{}'",
                vectorStoreType, topK, similarityThreshold, q);
        List<Document> searchResult = context.getVectorStore().similaritySearch(embeddingSearchRequest);
        List<Document> relevantDocuments = searchResult == null ? Collections.emptyList() : searchResult;
        log.info("RAG chat (single-turn) similaritySearch returned {} document(s)", relevantDocuments.size());
        if (relevantDocuments.isEmpty()) {
            log.warn("RAG chat (single-turn) retrieved 0 documents — check that indexing populated the store '{}' with the collection matching this site/locale",
                    vectorStoreType);
        } else if (log.isInfoEnabled()) {
            for (int i = 0; i < relevantDocuments.size(); i++) {
                Document d = relevantDocuments.get(i);
                String text = d.getText() == null ? "" : d.getText();
                log.info("  RAG chat (single-turn) doc[{}] metadata={} text=<<<\n{}\n>>>", i, d.getMetadata(), text);
            }
        }

        String information = relevantDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
        log.info("RAG chat (single-turn) raw system prompt template=<<<\n{}\n>>>", context.getSystemPrompt());
        String systemContent = buildSystemPrompt(context.getSystemPrompt(), information);
        log.info("RAG chat (single-turn) final system prompt sent to LLM=<<<\n{}\n>>>", systemContent);

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
            log.info("RAG chat (single-turn) full prompt (system + turns) sent to LLM=<<<{}\n>>>", dump);
        }
        String answer = context.getChatModel().call(prompt).getResult().getOutput().getText();
        log.info("RAG chat (single-turn) LLM answer=<<<\n{}\n>>>", answer);
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
        if (turSNSite.getTurSNSiteGenAi() == null || !turSNSite.getTurSNSiteGenAi().isEnabled()) {
            log.info("RAG skipped for Object ID '{}' of SN Site '{}': GenAI not enabled on site",
                    sourceId, turSNSite.getName());
            return;
        }
        String collectionName = resolveCollectionName(turSNSite, locale);
        log.info("RAG building context for Object ID '{}' of SN Site '{}' (collection='{}')",
                sourceId, turSNSite.getName(), collectionName);
        TurGenAiContext context = turGenAiContextFactory.build(
                turSNSite.getTurSNSiteGenAi(), collectionName);
        if (!context.isEnabled()) {
            log.warn("RAG context disabled for Object ID '{}' of SN Site '{}': check LLM, embedding model and store configuration",
                    sourceId, turSNSite.getName());
            return;
        }
        String text = extractIndexableText(doc);
        if (text.isEmpty()) {
            log.warn("RAG document for Object ID '{}' of SN Site '{}' has no indexable text (title/abstract/text attributes are empty)",
                    sourceId, turSNSite.getName());
        }
        addDocument(context, text, buildMetadata(turSNSite, locale, doc));
    }

    private String extractIndexableText(Map<String, Object> doc) {
        StringBuilder sb = new StringBuilder();
        String[] allowedAttributes = { TITLE, ABSTRACT, TEXT };
        for (String attr : allowedAttributes) {
            Object value = doc.get(attr);
            if (value != null) {
                sb.append(value).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    private Map<String, Object> buildMetadata(TurSNSite turSNSite, Locale locale, Map<String, Object> doc) {
        Map<String, Object> metadata = new HashMap<>();
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
        log.info("RAG addDocuments called with {} document(s)", turSNJobItems.getTuringDocuments().size());
        turSNJobItems.getTuringDocuments().forEach(jobItem -> jobItem.getSiteNames()
                .forEach(siteName -> turSNSearchProcess.getSNSite(siteName).ifPresentOrElse(turSNSite -> {
                    if (turSNSite.getTurSNSiteGenAi() == null || !turSNSite.getTurSNSiteGenAi().isEnabled()) {
                        log.info("RAG skipped for Object ID '{}' of SN Site '{}': GenAI not enabled on site",
                                jobItem.getId(), siteName);
                        return;
                    }
                    String collectionName = resolveCollectionName(turSNSite, jobItem.getLocale());
                    log.info("RAG building context for Object ID '{}' of SN Site '{}' (collection='{}')",
                            jobItem.getId(), siteName, collectionName);
                    TurGenAiContext context = turGenAiContextFactory.build(
                            turSNSite.getTurSNSiteGenAi(), collectionName);
                    if (!context.isEnabled()) {
                        log.warn("RAG context disabled for Object ID '{}' of SN Site '{}': check LLM, embedding model and store configuration",
                                jobItem.getId(), siteName);
                        return;
                    }
                    StringBuilder sb = new StringBuilder();
                    addAttributes(context, jobItem, sb);
                    if (sb.length() == 0) {
                        log.warn("RAG document for Object ID '{}' of SN Site '{}' has no indexable text (title/abstract/text attributes are empty)",
                                jobItem.getId(), siteName);
                    }
                    addDocument(context, sb.toString(), setMetadata(jobItem));
                }, () -> log.warn("RAG skipped for Object ID '{}': SN Site '{}' not resolved by search process",
                        jobItem.getId(), siteName))));
    }

    private void addDocument(TurGenAiContext context, String text, Map<String, Object> metadata) {
        List<Document> documents = TurRagUtils.createDocuments(text, metadata);
        String vectorStoreType = context.getVectorStore().getClass().getSimpleName();
        Object sourceId = metadata.get(SOURCE_ID);
        log.info("RAG indexing {} chunk(s) for Object ID '{}' into vector store '{}' — metadata={}",
                documents.size(), sourceId, vectorStoreType, metadata);
        for (int i = 0; i < documents.size(); i++) {
            Document chunk = documents.get(i);
            log.info("  RAG indexing chunk[{}/{}] Object ID '{}' text=<<<\n{}\n>>>",
                    i + 1, documents.size(), sourceId,
                    chunk.getText() == null ? "" : chunk.getText());
        }
        turRagContextBuilder.reindexByMetadata(context.getRagInfrastructure(), documents,
                SOURCE_ID, sourceId == null ? null : sourceId.toString());
        log.info("RAG indexed Object ID '{}' into vector store '{}' (previous embeddings for same source_id removed)",
                sourceId, vectorStoreType);
    }

    private void addAttributes(TurGenAiContext context, TurSNJobItem jobItem, StringBuilder sb) {
        if (context.isEnabled()) {
            String[] allowedAttributes = { TITLE, ABSTRACT, TEXT };
            jobItem.getAttributes().forEach((key, value) -> {
                if (Arrays.asList(allowedAttributes).contains(key))
                    sb.append(value).append(System.lineSeparator());
            });
        }
    }

    @NotNull
    private Map<String, Object> setMetadata(TurSNJobItem jobItem) {
        Map<String, Object> metadata = new HashMap<>();
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
        return metadata;
    }

}
