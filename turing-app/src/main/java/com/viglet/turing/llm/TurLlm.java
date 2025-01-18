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
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
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
    private final VectorStore vectorStore;

    public TurLlm(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void addDocuments(TurSNJobItems turSNJobItems) {
        System.out.println("addDocuments");
        List<Document> documents = new ArrayList<>();

        turSNJobItems.getTuringDocuments().forEach(jobItem -> {
            StringBuilder sb = new StringBuilder();
            addAttributes(jobItem, sb);
            documents.add(new Document(jobItem.getId(), sb.toString(), setMetadata(jobItem)));

        });
        vectorStore.add(splitDocuments(documents));
        System.out.println("added Documents");
    }

    private static void addAttributes(TurSNJobItem jobItem, StringBuilder sb) {
        String[] allowedAttributes = {TITLE, ABSTRACT, TEXT};
        jobItem.getAttributes().forEach((key, value) -> {
            if (Arrays.asList(allowedAttributes).contains(key))
                sb.append(key).append(": ").append(value).append(System.lineSeparator());
        });
    }

    @NotNull
    private static Map<String, Object> setMetadata(TurSNJobItem jobItem) {
        Map<String, Object> metadata = new HashMap<>();
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
        return metadata;
    }

    public List<Document> splitDocuments(List<Document> documents) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        return splitter.apply(documents);
    }
}
