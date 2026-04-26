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
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;

import java.util.ArrayList;
import java.util.List;

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
    /**
     * Returns the number of distinct values indexed under {@code fieldName}.
     * Reads Lucene's terms dictionary directly — fast and exact, since Lucene
     * already maintains a sorted index of unique terms per field. Returns
     * {@code 0} if the field has no indexed terms (e.g., absent metadata).
     * <p>
     * Used to compute the "documents" column on the collections admin page:
     * counting distinct {@code meta_source_id} values is more reliable than
     * parsing every chunk's stored {@code metadata_json}, because it does not
     * depend on JSON serialization or null-value quirks.
     */
    public synchronized long distinctTermCount(String fieldName) throws IOException {
        DirectoryReader reader = refreshReader();
        Terms terms = MultiTerms.getTerms(reader, fieldName);
        if (terms == null) {
            return 0L;
        }
        long count = 0L;
        TermsEnum it = terms.iterator();
        while (it.next() != null) {
            count++;
        }
        return count;
    }

    /**
     * A page of stored chunks returned by {@link #listChunks(Query, int, int)}.
     *
     * @param chunks the chunks belonging to the requested page
     * @param total  total number of documents matching the filter, across all
     *               pages — uses Lucene's {@code IndexSearcher.count} for an
     *               exact value
     */
    public record ChunkPage(List<StoredChunk> chunks, long total) {
    }

    /**
     * One stored chunk: id, raw stored content, and the JSON-serialized
     * metadata as it was written by {@code TurLuceneVectorStore.toLuceneDocument}.
     * Metadata parsing is left to the caller to avoid a dependency from
     * {@code TurLuceneVectorIndex} on the parser.
     */
    public record StoredChunk(String id, String content, String metadataJson) {
    }

    /**
     * Paginates over live documents matching {@code filter}, ordered by
     * indexed {@code id} so consecutive pages are stable across calls.
     * <p>
     * Implementation note: Lucene has no native {@code OFFSET}, so we
     * {@code search(query, offset + limit)} and skip the first {@code offset}
     * hits. Acceptable for admin-grade pagination (typically under a few
     * thousand chunks); for very deep paging consider {@code searchAfter}
     * with a cursor.
     *
     * @param filter Lucene query selecting eligible documents (e.g., a
     *               metadata-equality boolean clause). Pass {@code null} to
     *               match every live document.
     * @param offset zero-based offset into the result set (must be {@code >= 0})
     * @param limit  maximum number of chunks to return ({@code >= 1})
     */
    public synchronized ChunkPage listChunks(Query filter, int offset, int limit) throws IOException {
        if (offset < 0 || limit < 1) {
            return new ChunkPage(List.of(), 0L);
        }
        Query query = filter != null ? filter : new MatchAllDocsQuery();
        DirectoryReader reader = refreshReader();
        if (reader.numDocs() == 0) {
            return new ChunkPage(List.of(), 0L);
        }
        IndexSearcher searcher = new IndexSearcher(reader);
        long total = searcher.count(query);
        if (offset >= total) {
            return new ChunkPage(List.of(), total);
        }
        Sort sortById = new Sort(new SortField(TurLuceneVectorStore.FIELD_ID, SortField.Type.STRING));
        TopDocs topDocs = searcher.search(query, offset + limit, sortById);
        StoredFields storedFields = reader.storedFields();
        Set<String> fieldsToLoad = Set.of(
                TurLuceneVectorStore.FIELD_ID,
                TurLuceneVectorStore.FIELD_CONTENT,
                TurLuceneVectorStore.FIELD_METADATA_JSON);
        List<StoredChunk> chunks = new ArrayList<>(Math.min(limit, topDocs.scoreDocs.length));
        for (int i = offset; i < topDocs.scoreDocs.length && chunks.size() < limit; i++) {
            int docId = topDocs.scoreDocs[i].doc;
            var doc = storedFields.document(docId, fieldsToLoad);
            chunks.add(new StoredChunk(
                    doc.get(TurLuceneVectorStore.FIELD_ID),
                    doc.get(TurLuceneVectorStore.FIELD_CONTENT),
                    doc.get(TurLuceneVectorStore.FIELD_METADATA_JSON)));
        }
        return new ChunkPage(chunks, total);
    }

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
