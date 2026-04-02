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
package com.viglet.turing.lucene;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;

import lombok.extern.slf4j.Slf4j;

/**
 * Holds the Lucene index state for a single core (index directory).
 * Wraps the {@link IndexWriter}, {@link Directory}, and provides a refreshable
 * {@link IndexSearcher} that always reflects the latest committed documents.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
public class TurLuceneInstance {

    private final IndexWriter indexWriter;
    private final Directory directory;
    private final Path indexPath;
    private final FacetsConfig facetsConfig;
    private final AtomicReference<DirectoryReader> directoryReader;

    public TurLuceneInstance(IndexWriter indexWriter, Directory directory, Path indexPath,
            FacetsConfig facetsConfig) throws IOException {
        this.indexWriter = indexWriter;
        this.directory = directory;
        this.indexPath = indexPath;
        this.facetsConfig = facetsConfig;
        this.directoryReader = new AtomicReference<>(DirectoryReader.open(indexWriter));
    }

    /**
     * Returns an {@link IndexSearcher} backed by the latest index state.
     * If the underlying index has changed since the last call, the reader is
     * refreshed.
     */
    public synchronized IndexSearcher getSearcher() throws IOException {
        DirectoryReader current = directoryReader.get();
        DirectoryReader newReader = DirectoryReader.openIfChanged(current, indexWriter);
        if (newReader != null) {
            current.close();
            directoryReader.set(newReader);
        }
        return new IndexSearcher(directoryReader.get());
    }

    /**
     * Returns the current {@link DirectoryReader}, refreshing it if the index has
     * changed.
     */
    public synchronized DirectoryReader getReader() throws IOException {
        DirectoryReader current = directoryReader.get();
        DirectoryReader newReader = DirectoryReader.openIfChanged(current, indexWriter);
        if (newReader != null) {
            current.close();
            directoryReader.set(newReader);
        }
        return directoryReader.get();
    }

    public IndexWriter getWriter() {
        return indexWriter;
    }

    public Path getIndexPath() {
        return indexPath;
    }

    /**
     * Returns the {@link FacetsConfig} used when building facet-enabled documents.
     */
    public FacetsConfig getFacetsConfig() {
        return facetsConfig;
    }

    public void close() throws IOException {
        try {
            directoryReader.get().close();
        } finally {
            try {
                indexWriter.close();
            } finally {
                directory.close();
            }
        }
    }
}
