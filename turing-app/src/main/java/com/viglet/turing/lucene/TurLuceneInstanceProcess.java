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

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and caches {@link TurLuceneInstance} objects per index path.
 * Mirrors {@code TurSolrInstanceProcess} for the Lucene engine.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
public class TurLuceneInstanceProcess {

    private final ConcurrentHashMap<String, TurLuceneInstance> instanceCache = new ConcurrentHashMap<>();
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurLuceneStorageSync storageSync;

    public TurLuceneInstanceProcess(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurLuceneStorageSync storageSync) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.storageSync = storageSync;
    }

    /**
     * Initialises (or returns a cached) Lucene instance for the given site + locale.
     */
    public Optional<TurLuceneInstance> initLuceneInstance(String siteName, Locale locale) {
        return turSNSiteRepository.findByNameIgnoreCase(siteName)
                .flatMap(site -> initLuceneInstance(site, locale));
    }

    private Optional<TurLuceneInstance> initLuceneInstance(TurSNSite turSNSite, Locale locale) {
        TurSNSiteLocale siteLocale = turSNSiteLocaleRepository.findByTurSNSiteAndLanguage(turSNSite, locale);
        if (siteLocale == null) {
            log.warn("{} site with {} locale not found", turSNSite.getName(), locale);
            return Optional.empty();
        }
        return getLuceneInstance(turSNSite.getTurSEInstance(), siteLocale.getCore());
    }

    /**
     * Initialises (or returns a cached) Lucene instance for the given SE instance + core name.
     */
    public Optional<TurLuceneInstance> initLuceneInstance(TurSEInstance turSEInstance, String coreName) {
        return getLuceneInstance(turSEInstance, coreName);
    }

    public Optional<TurLuceneInstance> initLuceneInstance(TurSNSiteLocale turSNSiteLocale) {
        return getLuceneInstance(turSNSiteLocale.getTurSNSite().getTurSEInstance(),
                turSNSiteLocale.getCore());
    }

    private static final String LUCENE_BASE_DIR = "store/lucene";

    private static Path resolveIndexPath(String endpointUrl, String coreName) {
        Path base = Paths.get(endpointUrl);
        if (base.isAbsolute()) {
            return base.resolve(coreName);
        }
        return Paths.get(LUCENE_BASE_DIR, endpointUrl, coreName);
    }

    private Optional<TurLuceneInstance> getLuceneInstance(TurSEInstance turSEInstance, String coreName) {
        Path indexPath = resolveIndexPath(turSEInstance.getEndpointUrl(), coreName);
        try {
            return openInstance(indexPath);
        } catch (Exception e) {
            log.error("Error obtaining Lucene instance for core '{}' at '{}'", coreName,
                    turSEInstance.getEndpointUrl(), e);
            return Optional.empty();
        }
    }

    /** Returns the cached instance for {@code indexPath}, opening (and caching) one if absent. */
    private Optional<TurLuceneInstance> openInstance(Path indexPath) {
        TurLuceneInstance instance = instanceCache.computeIfAbsent(indexPath.toString(), key -> {
            try {
                return createInstance(indexPath);
            } catch (IOException e) {
                log.error("Failed to create Lucene instance for path: {}", indexPath, e);
                return null;
            }
        });
        return Optional.ofNullable(instance);
    }

    /**
     * Recovers from an orphaned/closed {@link IndexWriter} — e.g. the core directory was
     * deleted and recreated underneath a cached writer, leaving {@code write.lock} missing
     * and the writer tragically closed. Closes and removes the stale cached instance
     * <b>without deleting index files</b>, then reopens a fresh one. Unlike
     * {@link #resetInstance(Path)} this preserves any existing index data.
     */
    public Optional<TurLuceneInstance> rebuildInstance(Path indexPath) {
        TurLuceneInstance removed = instanceCache.remove(indexPath.toString());
        if (removed != null) {
            try {
                removed.close();
            } catch (IOException e) {
                log.warn("Error closing stale Lucene instance during rebuild '{}': {}",
                        indexPath, e.getMessage());
            }
        }
        return openInstance(indexPath);
    }

    private TurLuceneInstance createInstance(Path indexPath) throws IOException {
        Files.createDirectories(indexPath);
        // Warm-up: download segments from storage if local index is empty
        if (storageSync != null && storageSync.isEnabled()) {
            String coreName = indexPath.getFileName().toString();
            if (isLocalIndexEmpty(indexPath)) {
                log.info("Local index empty, attempting warm-up from storage for core: {}", coreName);
                storageSync.syncFromStorage(indexPath, coreName);
            }
        }
        log.info("Opening Lucene index at: {}", indexPath);
        Directory directory = FSDirectory.open(indexPath);
        IndexWriterConfig config = new IndexWriterConfig(
                new TurLucenePerFieldAnalyzer());
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        IndexWriter writer = new IndexWriter(directory, config);
        return new TurLuceneInstance(writer, directory, indexPath, new FacetsConfig());
    }

    /** Evicts a cached instance (e.g. after a core is deleted). */
    public void evict(String basePath, String coreName) {
        String cacheKey = resolveIndexPath(basePath, coreName).toString();
        TurLuceneInstance removed = instanceCache.remove(cacheKey);
        if (removed != null) {
            try {
                removed.close();
            } catch (IOException e) {
                log.error("Error closing evicted Lucene instance for '{}'", cacheKey, e);
            }
        }
    }

    /**
     * Closes and evicts the cached instance for {@code indexPath}, then deletes the
     * index directory. The next call to {@code initLuceneInstance} will recreate
     * a fresh empty index. Used to recover from Lucene schema conflicts.
     */
    public void resetInstance(Path indexPath) {
        String cacheKey = indexPath.toString();
        TurLuceneInstance removed = instanceCache.remove(cacheKey);
        if (removed != null) {
            try {
                removed.close();
            } catch (IOException e) {
                log.error("Error closing Lucene instance during reset: {}", cacheKey, e);
            }
        }
        try {
            if (Files.exists(indexPath)) {
                Files.walk(indexPath)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException ex) {
                                log.warn("Could not delete index file '{}': {}", p, ex.getMessage());
                            }
                        });
                log.info("Lucene index directory deleted for reset: {}", indexPath);
            }
        } catch (IOException e) {
            log.error("Error deleting index directory during reset: {}", indexPath, e);
        }
        // Also clean up storage
        if (storageSync != null && storageSync.isEnabled()) {
            storageSync.deleteFromStorage(indexPath.getFileName().toString());
        }
    }

    private static boolean isLocalIndexEmpty(Path indexPath) throws IOException {
        if (!Files.isDirectory(indexPath)) return true;
        try (var files = Files.list(indexPath)) {
            return files.noneMatch(p -> p.getFileName().toString().startsWith("segments"));
        }
    }

    @PreDestroy
    public void shutdown() {
        instanceCache.values().forEach(instance -> {
            try {
                instance.close();
            } catch (IOException e) {
                log.error("Error closing Lucene instance at shutdown", e);
            }
        });
        instanceCache.clear();
    }
}
