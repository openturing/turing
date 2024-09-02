/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.lucene;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.FSDirectory;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

import static com.viglet.turing.lucene.TurLuceneConstants.FULL_TEXT;
import static com.viglet.turing.lucene.TurLuceneConstants.ID;
import static com.viglet.turing.lucene.TurLuceneUtils.getLuceneDirectory;

@Slf4j
@Component
public class TurLuceneWriter {
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    @Inject
    public TurLuceneWriter(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    public void indexing(TurSNSite turSNSite, Map<String, Object> attributes) {
        try {
            indexDocument(createIndexDocument(turSNSite, attributes));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    public void indexDocument(Document document) {
        getLuceneDirectory().ifPresent(directory -> {
            try (IndexWriter writer = getIndexWriter(directory)) {
                writer.addDocument(document);
                commit(writer);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        });
    }

    private static void commit(IndexWriter writer) throws IOException {
        writer.flush();
        writer.commit();
    }

    @NotNull
    private static IndexWriter getIndexWriter(FSDirectory directory) throws IOException {
        return new IndexWriter(directory, new IndexWriterConfig());
    }

    public void updateDocumentIndex(String id, TurSNSite turSNSite, Map<String, Object> attributes) {
        getLuceneDirectory().ifPresent(directory -> {
            try (IndexWriter writer = getIndexWriter(directory)) {
                Document document = createIndexDocument(turSNSite, attributes);
                writer.updateDocument(new Term(ID, id), document);
                commit(writer);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        });
    }

    public Document createIndexDocument(TurSNSite turSNSite, Map<String, Object> fields) {
        Document document = new Document();
        StringBuffer text = new StringBuffer();
        fields.forEach((fieldName, fieldValue) -> getField(turSNSite, fieldName, fieldValue, document, text));
        getFullTextField(document, text);
        return document;
    }

    private static void getFullTextField(Document document, StringBuffer text) {
        document.add(new TextField(FULL_TEXT, text.toString(), Field.Store.YES));
    }

    private void getField(TurSNSite turSNSite, String fieldName, Object fieldValue, Document document,
                          StringBuffer text) {

        turSNSiteFieldExtRepository.findByTurSNSiteAndName(turSNSite, fieldName)
                .stream()
                .findFirst()
                .ifPresent(turSNSiteFieldExtension ->
                        fieldsByType(fieldName, fieldValue, turSNSiteFieldExtension)
                                .forEach(field -> {
                                    document.add(field);
                                    text.append(field.toString());
                                }));

    }

    private static List<IndexableField> fieldsByType(String fieldName, Object fieldValue,
                                                     TurSNSiteFieldExt turSNSiteFieldExtension) {
        return switch (turSNSiteFieldExtension.getType()) {
            case LONG -> List.of(new LongField(fieldName, (Long) fieldValue, Field.Store.YES));
            case DATE -> List.of(new StoredField(fieldName, ((Date) fieldValue).getTime()));
            case INT -> List.of(new IntField(fieldName, (Integer) fieldValue, Field.Store.YES));
            case TEXT -> List.of(new TextField(fieldName, fieldValue.toString(), Field.Store.YES));
            case ARRAY -> {
                List<IndexableField> fields = new ArrayList<>();
                Arrays.asList((Object[]) fieldValue).forEach(item ->
                        fields.add(new StringField(fieldName, item.toString(), Field.Store.YES)));
                yield fields;
            }
            case null, default -> List.of(new StringField(fieldName, fieldValue.toString(), Field.Store.YES));
        };
    }

    public void deleteAllIndexes() {
        getLuceneDirectory().ifPresent(directory -> {
            try (IndexWriter writer = getIndexWriter(directory)) {
                writer.deleteAll();
                commit(writer);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        });
    }

    public void deleteDocumentById(String id) {
        getLuceneDirectory().ifPresent(directory -> {
            try (IndexWriter writer = getIndexWriter(directory)) {
                writer.deleteDocuments(new Term(ID, id));
                commit(writer);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        });
    }
}
