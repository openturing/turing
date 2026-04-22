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
import org.springframework.ai.chat.prompt.PromptTemplate;
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
    public static final String QUESTION = "question";
    public static final String INFORMATION = "information";
    public static final String DEFAULT_PROMPT = "Use the provided information to answer the question.\nQuestion: {question}\nInformation: {information}";
    private final TurSNSearchProcess turSNSearchProcess;
    private final TurGenAiContextFactory turGenAiContextFactory;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;

    public TurSNGenAi(TurSNSearchProcess turSNSearchProcess,
            TurGenAiContextFactory turGenAiContextFactory,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.turGenAiContextFactory = turGenAiContextFactory;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
    }

    public TurChatMessage assistant(TurGenAiContext context, String q) {
        if (context.isEnabled()) {
            int tokenCounter = new StringTokenizer(q).countTokens();
            if (tokenCounter > 1) {
                return getTurChatMessage(context, q);
            } else {
                if (tokenCounter == 1 && !q.contains("*")) {
                    return getTurChatMessage(context, "what is %s?".formatted(q));
                } else {
                    return TurChatMessage.builder().text(null).enabled(true).build();
                }
            }
        } else {
            return TurChatMessage.builder().text("AI configuration is not enabled").enabled(false).build();
        }
    }

    /**
     * Conversational RAG: retrieves documents for the latest user message and
     * calls the ChatModel with the full conversation history so follow-up
     * questions preserve context.
     *
     * @author Alexandre Oliveira
     * @since 2026.2.4
     */
    public TurChatMessage assistantConversation(TurGenAiContext context, List<ConversationMessage> history) {
        if (!context.isEnabled()) {
            return TurChatMessage.builder().text("AI configuration is not enabled").enabled(false).build();
        }
        if (history == null || history.isEmpty()) {
            return TurChatMessage.builder().text(null).enabled(true).build();
        }

        String latestUserQuery = history.stream()
                .filter(m -> "user".equals(m.role()))
                .map(ConversationMessage::content)
                .reduce((first, second) -> second)
                .orElse("");
        if (latestUserQuery.isBlank()) {
            return TurChatMessage.builder().text(null).enabled(true).build();
        }

        SearchRequest embeddingSearchRequest = SearchRequest.builder()
                .query(latestUserQuery)
                .topK(10)
                .similarityThreshold(0.7)
                .build();
        List<Document> relevantDocuments = context.getVectorStore().similaritySearch(embeddingSearchRequest);
        String information = relevantDocuments == null ? "" : relevantDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        String systemContent = buildConversationSystemPrompt(context.getSystemPrompt(), information);

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
        String answer = context.getChatModel().call(prompt).getResult().getOutput().getText();
        return TurChatMessage.builder().text(answer).enabled(true).build();
    }

    private String buildConversationSystemPrompt(String configuredPrompt, String information) {
        String base;
        if (configuredPrompt == null || configuredPrompt.isBlank()) {
            base = "Use the provided information to answer the user's questions. "
                    + "If the information is not enough, say so. "
                    + "Keep the conversation coherent with previous turns.";
        } else {
            // Strip {question} placeholder; substitute {information} if present.
            base = configuredPrompt
                    .replace("{question}", "")
                    .replace("{information}", information);
        }
        if (!base.contains(information) && !information.isBlank()) {
            base = base + "\n\nInformation:\n" + information;
        }
        return base;
    }

    public record ConversationMessage(String role, String content) {
    }

    private TurChatMessage getTurChatMessage(TurGenAiContext context, String q) {
        SearchRequest embeddingSearchRequest = SearchRequest.builder()
                .query(q)
                .topK(10)
                .similarityThreshold(0.7)
                .build();
        List<Document> relevantDocuments = context.getVectorStore().similaritySearch(embeddingSearchRequest);
        if (relevantDocuments.isEmpty()) {
            relevantDocuments = Collections.emptyList();
        }

        PromptTemplate promptTemplate = new PromptTemplate(resolvePromptTemplate(context.getSystemPrompt()));
        String information = relevantDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        Map<String, Object> variables = new HashMap<>();
        variables.put(QUESTION, q);
        variables.put(INFORMATION, information);
        Prompt prompt = promptTemplate.create(variables);
        return TurChatMessage.builder()
                .text(context.getChatModel().call(prompt).getResult().getOutput().getText())
                .enabled(true)
                .build();
    }

    public void addDocuments(TurSNJobItems turSNJobItems) {
        turSNJobItems.getTuringDocuments().forEach(jobItem -> jobItem.getSiteNames()
                .forEach(siteName -> turSNSearchProcess.getSNSite(siteName).ifPresent(turSNSite -> {
                    if (turSNSite.getTurSNSiteGenAi() != null && turSNSite.getTurSNSiteGenAi().isEnabled()) {
                        String collectionName = resolveCollectionName(turSNSite, jobItem);
                        TurGenAiContext context = turGenAiContextFactory.build(
                                turSNSite.getTurSNSiteGenAi(), collectionName);
                        if (context.isEnabled()) {
                            StringBuilder sb = new StringBuilder();
                            addAttributes(context, jobItem, sb);
                            addDocument(context, sb.toString(), setMetadata(jobItem));
                        }
                    }
                })));
    }

    private String resolveCollectionName(TurSNSite turSNSite, TurSNJobItem jobItem) {
        if (jobItem.getLocale() != null) {
            Locale locale = jobItem.getLocale();
            TurSNSiteLocale siteLocale = turSNSiteLocaleRepository
                    .findByTurSNSiteAndLanguage(turSNSite, locale);
            if (siteLocale != null && siteLocale.getCore() != null) {
                return siteLocale.getCore();
            }
        }
        return null;
    }

    private void addDocument(TurGenAiContext context, String text, Map<String, Object> metadata) {
        List<Document> documents = TurRagUtils.createDocuments(text, metadata);
        context.getVectorStore().add(documents);
        log.info("added document to embedding store: {}", metadata.get(SOURCE_ID));
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

    private String resolvePromptTemplate(String configuredPrompt) {
        return (configuredPrompt == null || configuredPrompt.isBlank()) ? DEFAULT_PROMPT : configuredPrompt;
    }
}
