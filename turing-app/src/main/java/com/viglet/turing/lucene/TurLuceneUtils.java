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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.store.FSDirectory;

import com.viglet.turing.solr.bean.TurSECoreInfo;

import lombok.extern.slf4j.Slf4j;

/**
 * Static utilities for Lucene core (index directory) management.
 * Mirrors {@code TurSolrUtils} for the Lucene engine.
 *
 * <p>
 * In Lucene, a "core" corresponds to a directory on the filesystem that
 * contains a Lucene index. The {@code basePath} is the value of
 * {@code TurSEInstance.endpointUrl} for Lucene-backed SE instances.
 *
 * <p>
 * Field management is intentionally a no-op: Lucene is schema-less and
 * field types are tracked in the Turing JPA model (TurSNSiteField).
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
public final class TurLuceneUtils {

    private static final String LUCENE_BASE_DIR = "store/lucene";

    private TurLuceneUtils() {
        throw new IllegalStateException("Lucene Utility class");
    }

    private static Path resolvePath(String basePath, String coreName) {
        Path base = Path.of(basePath);
        if (base.isAbsolute()) {
            return base.resolve(coreName);
        }
        return Path.of(LUCENE_BASE_DIR, basePath, coreName);
    }

    private static Path resolveBasePath(String basePath) {
        Path base = Path.of(basePath);
        if (base.isAbsolute()) {
            return base;
        }
        return Path.of(LUCENE_BASE_DIR, basePath);
    }

    // -------------------------------------------------------------------------
    // Core management
    // -------------------------------------------------------------------------

    /**
     * Creates the directory for a Lucene core.
     *
     * @param basePath the root path of the Lucene SE instance (endpointUrl)
     * @param coreName the core name (sub-directory)
     */
    public static void createCore(String basePath, String coreName) {
        Path corePath = resolvePath(basePath, coreName);
        try {
            Files.createDirectories(corePath);
            log.info("Lucene core created at: {}", corePath);
        } catch (IOException e) {
            log.error("Failed to create Lucene core at {}: {}", corePath, e.getMessage(), e);
        }
    }

    /**
     * Deletes the directory (and all index files) for a Lucene core.
     *
     * @param basePath the root path of the Lucene SE instance (endpointUrl)
     * @param coreName the core name (sub-directory)
     */
    public static void deleteCore(String basePath, String coreName) {
        Path corePath = resolvePath(basePath, coreName);
        if (!Files.exists(corePath)) {
            log.warn("Lucene core path does not exist, nothing to delete: {}", corePath);
            return;
        }
        try (Stream<Path> walk = Files.walk(corePath)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.error("Failed to delete {}: {}", p, e.getMessage());
                        }
                    });
            log.info("Lucene core deleted: {}", corePath);
        } catch (IOException e) {
            log.error("Failed to delete Lucene core at {}: {}", corePath, e.getMessage(), e);
        }
    }

    /**
     * Returns true if the given core directory exists and is a directory.
     */
    public static boolean coreExists(String basePath, String coreName) {
        return Files.isDirectory(resolvePath(basePath, coreName));
    }

    /**
     * Lists all direct sub-directories of {@code basePath} as cores. Opens each
     * index briefly with a {@link DirectoryReader} to read the live document
     * count; cores that haven't been written yet (no segments) report 0
     * without raising the missing-index error.
     */
    public static List<TurSECoreInfo> listCores(String basePath) {
        Path base = resolveBasePath(basePath);
        if (!Files.isDirectory(base)) {
            return Collections.emptyList();
        }
        try (Stream<Path> dirs = Files.list(base)) {
            return dirs.filter(Files::isDirectory)
                    .sorted()
                    .map(p -> new TurSECoreInfo(p.getFileName().toString(), readNumDocs(p), Collections.emptyList()))
                    .toList();
        } catch (IOException e) {
            log.error("Failed to list Lucene cores at {}: {}", basePath, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Best-effort live document count for a Lucene core directory. Returns 0
     * when the directory has no segments yet ({@link IndexNotFoundException})
     * or when the reader can't open the directory for any reason — listing the
     * core is more important than failing the whole request over a single
     * unreadable index.
     */
    private static long readNumDocs(Path corePath) {
        try (FSDirectory dir = FSDirectory.open(corePath);
                DirectoryReader reader = DirectoryReader.open(dir)) {
            return reader.numDocs();
        } catch (IndexNotFoundException e) {
            return 0L;
        } catch (IOException e) {
            log.warn("Failed to read Lucene numDocs at {}: {}", corePath, e.getMessage());
            return 0L;
        }
    }

    // -------------------------------------------------------------------------
    // Field management (no-ops — Lucene is schema-less)
    // -------------------------------------------------------------------------

    /**
     * No-op for Lucene: field types are tracked in the JPA model; the index
     * itself is schema-less and accepts any field at index time.
     */
    public static void addOrUpdateField(String coreName, String fieldName) {
        log.debug("Lucene addOrUpdateField: no-op for field '{}' in core '{}'", fieldName, coreName);
    }

    /**
     * No-op for Lucene: removing a field definition only requires updating the
     * JPA model — existing indexed values are not retroactively removed.
     */
    public static void deleteField(String coreName, String fieldName) {
        log.debug("Lucene deleteField: no-op for field '{}' in core '{}'", fieldName, coreName);
    }
}
