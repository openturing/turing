/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.genai.provider.store.lucene;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;

import lombok.extern.slf4j.Slf4j;

/**
 * Encapsulates per-collection Lucene resources for the vector store: the {@link Directory}, a shared
 * {@link IndexWriter}, and a refreshable {@link DirectoryReader}.
 * <p>
 * Instances are expected to be long-lived and shared across {@link TurLuceneVectorStore}
 * invocations by {@link TurLuceneStoreProvider}. Lucene's {@link FSDirectory} holds an exclusive
 * write lock, so only one writer per index path can exist within a JVM — sharing is mandatory.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
public class TurLuceneVectorIndex {

    private final Path indexPath;
    private final Directory directory;
    private final IndexWriter indexWriter;
    private final AtomicReference<DirectoryReader> directoryReader;

    public TurLuceneVectorIndex(Path indexPath) throws IOException {
        this.indexPath = indexPath;
        this.directory = FSDirectory.open(indexPath);
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.indexWriter = new IndexWriter(directory, config);
        this.indexWriter.commit();
        this.directoryReader = new AtomicReference<>(DirectoryReader.open(indexWriter));
    }

    public IndexWriter getWriter() {
        return indexWriter;
    }

    public Path getIndexPath() {
        return indexPath;
    }

    /**
     * Returns a fresh {@link IndexSearcher} reflecting the latest committed documents.
     */
    public synchronized IndexSearcher getSearcher() throws IOException {
        return new IndexSearcher(refreshReader());
    }

    public synchronized long numDocs() throws IOException {
        return refreshReader().numDocs();
    }

    /**
     * Iterates stored fields of every live document in the index and hands the value of the
     * given field to {@code consumer}. Used by callers that need to compute aggregate statistics
     * over stored metadata (e.g., distinct document count across chunks).
     */
    public synchronized void visitStoredField(String fieldName, Consumer<String> consumer) throws IOException {
        DirectoryReader reader = refreshReader();
        Bits liveDocs = MultiBits.getLiveDocs(reader);
        StoredFields storedFields = reader.storedFields();
        Set<String> fieldsToLoad = Set.of(fieldName);
        int maxDoc = reader.maxDoc();
        for (int docId = 0; docId < maxDoc; docId++) {
            if (liveDocs != null && !liveDocs.get(docId)) {
                continue;
            }
            var doc = storedFields.document(docId, fieldsToLoad);
            String value = doc.get(fieldName);
            if (value != null) {
                consumer.accept(value);
            }
        }
    }

    private DirectoryReader refreshReader() throws IOException {
        DirectoryReader current = directoryReader.get();
        DirectoryReader refreshed = DirectoryReader.openIfChanged(current, indexWriter);
        if (refreshed != null) {
            current.close();
            directoryReader.set(refreshed);
        }
        return directoryReader.get();
    }

    public void clear() throws IOException {
        indexWriter.deleteAll();
        indexWriter.commit();
    }

    public void close() {
        try {
            DirectoryReader current = directoryReader.get();
            if (current != null) {
                current.close();
            }
        } catch (IOException e) {
            log.warn("[LuceneVectorIndex] Failed to close reader at {}: {}", indexPath, e.getMessage());
        }
        try {
            indexWriter.close();
        } catch (IOException e) {
            log.warn("[LuceneVectorIndex] Failed to close writer at {}: {}", indexPath, e.getMessage());
        }
        try {
            directory.close();
        } catch (IOException e) {
            log.warn("[LuceneVectorIndex] Failed to close directory at {}: {}", indexPath, e.getMessage());
        }
    }
}
