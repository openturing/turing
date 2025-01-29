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

package com.viglet.turing.llm;

import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TurLlm {

    public static final String MODIFICATION_DATE = "modification_date";
    public static final String PUBLICATION_DATE = "publication_date";
    public static final String URL = "url";
    public static final String ID = "id";
    public static final String LOCALE = "locale";
    public static final String SOURCE_APPS = "sourceApps";
    public static final String SITES = "sites";
    public static final String TITLE = "title";
    public static final String ABSTRACT = "abstract";
    public static final String TEXT = "text";
    private final ChromaEmbeddingStore chromaEmbeddingStore;
    private final EmbeddingModel embeddingModel;
    private final static boolean enabled = false;
    public TurLlm() {
        if (enabled) {

            chromaEmbeddingStore = ChromaEmbeddingStore.builder()
                    .baseUrl("http://localhost:8000/")
                    .collectionName("turing")
                    .logRequests(true)
                    .logResponses(true)
                    .build();
            embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl("http://localhost:11434")
                    .logRequests(true)
                    .logResponses(true)
                    .modelName("mistral")
                    .build();
        }
        else {
            chromaEmbeddingStore = null;
            embeddingModel =  null;
        }
    }

    public void addDocuments(TurSNJobItems turSNJobItems) {
        if (enabled) {
            System.out.println("addDocuments");
            turSNJobItems.getTuringDocuments().forEach(jobItem -> {
                StringBuilder sb = new StringBuilder();
                addAttributes(jobItem, sb);

                TextSegment segment1 = TextSegment.from(sb.toString(), new Metadata(setMetadata(jobItem)));
                Embedding embedding1 = embeddingModel.embed(segment1).content();
                chromaEmbeddingStore.add(embedding1, segment1);
            });
            System.out.println("added Documents");
        }
    }

    private static void addAttributes(TurSNJobItem jobItem, StringBuilder sb) {
        if (enabled) {
            String[] allowedAttributes = {TITLE, ABSTRACT, TEXT};
            jobItem.getAttributes().forEach((key, value) -> {
                if (Arrays.asList(allowedAttributes).contains(key))
                    sb.append(value).append(System.lineSeparator());
            });
        }
    }

    @NotNull
    private static Map<String, Object> setMetadata(TurSNJobItem jobItem) {
        Map<String, Object> metadata = new HashMap<>();
        if (enabled) {
            metadata.put(ID, jobItem.getId());
            metadata.put(LOCALE, jobItem.getLocale());
            metadata.put(SOURCE_APPS, jobItem.getProviderName());
            metadata.put(SITES, jobItem.getSiteNames());
            if (jobItem.getAttributes().containsKey(MODIFICATION_DATE)) {
                metadata.put(MODIFICATION_DATE, jobItem.getAttributes().get(MODIFICATION_DATE));
            }
            if (jobItem.getAttributes().containsKey(PUBLICATION_DATE)) {
                metadata.put(PUBLICATION_DATE, jobItem.getAttributes().get(PUBLICATION_DATE));
            }
            if (jobItem.getAttributes().containsKey(URL)) {
                metadata.put(URL, jobItem.getAttributes().get(URL));
            }
        }
        return metadata;
    }
}
