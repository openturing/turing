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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Component;

import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.sn.TurSNSearchProcess;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TurGenAi {
    public static final String SITES = "sites";
    public static final String LOCALE = "locale";
    public static final String QUESTION = "question";
    public static final String INFORMATION = "information";
    public static final int CHUNK_SIZE = 1024;
    public static final String DEFAULT_PROMPT = "Use the provided information to answer the question.\nQuestion: {question}\nInformation: {information}";
    private final TurSNSearchProcess turSNSearchProcess;
    private final TurGenAiContextFactory turGenAiContextFactory;

    public TurGenAi(TurSNSearchProcess turSNSearchProcess,
            TurGenAiContextFactory turGenAiContextFactory) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.turGenAiContextFactory = turGenAiContextFactory;
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
                    TurGenAiContext context = turGenAiContextFactory.build(turSNSite.getTurSNSiteGenAi());
                    if (context.isEnabled()) {
                        StringBuilder sb = new StringBuilder();
                        addAttributes(context, jobItem, sb);
                        addDocument(context, sb.toString(), setMetadata(jobItem));
                    }
                })));
    }

    private void addDocument(TurGenAiContext context, String text, Map<String, Object> metadata) {
        List<Document> documents = splitText(text, CHUNK_SIZE).stream()
                .map(chunk -> new Document(chunk, metadata))
                .toList();
        context.getVectorStore().add(documents);
        log.info("added document to embedding store: {}", metadata.get(ID));
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
        metadata.put(ID, jobItem.getId());
        metadata.put(LOCALE, jobItem.getLocale().toString());
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

    private List<String> splitText(String text, int chunkSize) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (text.length() <= chunkSize) {
            return List.of(text);
        }
        int chunks = (int) Math.ceil((double) text.length() / chunkSize);
        List<String> result = new java.util.ArrayList<>(chunks);
        for (int start = 0; start < text.length(); start += chunkSize) {
            int end = Math.min(text.length(), start + chunkSize);
            result.add(text.substring(start, end));
        }
        return result;
    }
}
