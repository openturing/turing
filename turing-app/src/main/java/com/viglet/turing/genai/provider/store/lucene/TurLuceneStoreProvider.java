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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.exception.TurRuntimeException;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProvider;
import com.viglet.turing.genai.provider.store.TurStoreChunkPage;
import com.viglet.turing.genai.provider.store.TurStoreCollectionInfo;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Embedded vector store provider backed by Apache Lucene 10 KNN. Each collection maps to a single
 * directory under the configured base path, and is persisted on the local filesystem.
 * <p>
 * Configuration (all optional, inside {@code providerOptionsJson}):
 * <ul>
 *   <li>{@code basePath} — root directory for all Lucene vector indexes (default:
 *       {@code ./store/lucene-vector}). May also be supplied via {@link TurStoreInstance#url}.</li>
 *   <li>{@code collectionName} — default collection name when none is passed (falls back to
 *       {@link TurStoreInstance#collectionName} then to {@code turing}).</li>
 * </ul>
 * <p>
 * This provider owns the Lucene {@link TurLuceneVectorIndex} cache keyed by ({@code basePath},
 * {@code collectionName}); {@link VectorStore} instances returned from {@code createVectorStore}
 * are lightweight wrappers pointing at the shared index.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Component
public class TurLuceneStoreProvider implements TurGenAiStoreProvider {

    private static final String DEFAULT_BASE_PATH = "./store/lucene-vector";
    private static final String DEFAULT_COLLECTION = "turing";
    private static final String BASE_PATH_OPTION = "basePath";
    private static final String COLLECTION_NAME_OPTION = "collectionName";

    /**
     * Metadata keys used to identify a logical document across its chunks, checked in order.
     * Mirrors the COALESCE chain used by {@code TurPgVectorStoreProvider}.
     */
    private static final List<String> DOCUMENT_IDENTIFIER_KEYS = List.of("source_id", "objectName", "fileName");

    private final TurProviderOptionsParser optionsParser;
    private final com.viglet.turing.tenant.TurTenantContext tenantContext;
    private final ConcurrentHashMap<String, TurLuceneVectorIndex> indexCache = new ConcurrentHashMap<>();

    public TurLuceneStoreProvider(TurProviderOptionsParser optionsParser,
            com.viglet.turing.tenant.TurTenantContext tenantContext) {
        this.optionsParser = optionsParser;
        this.tenantContext = tenantContext;
    }

    @Override
    public String getPluginType() {
        return "lucene";
    }

    @PreDestroy
    public void shutdown() {
        indexCache.values().forEach(TurLuceneVectorIndex::close);
        indexCache.clear();
    }

    @Override
    public VectorStore createVectorStore(TurStoreInstance turStoreInstance,
            EmbeddingModel embeddingModel,
            String decryptedCredential) {
        return createVectorStore(turStoreInstance, embeddingModel, decryptedCredential, null);
    }

    @Override
    public VectorStore createVectorStore(TurStoreInstance turStoreInstance,
            EmbeddingModel embeddingModel,
            String decryptedCredential,
            String collectionNameOverride) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        Path basePath = resolveBasePath(turStoreInstance, options);
        String collectionName = resolveCollectionName(turStoreInstance, options, collectionNameOverride);
        TurLuceneVectorIndex index = openIndex(basePath, collectionName);
        return new TurLuceneVectorStore(index, embeddingModel, collectionName);
    }

    @Override
    public List<TurStoreCollectionInfo> listCollections(TurStoreInstance turStoreInstance,
            String decryptedCredential) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        Path basePath = resolveBasePath(turStoreInstance, options);
        if (!Files.isDirectory(basePath)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(basePath)) {
            List<TurStoreCollectionInfo> result = new ArrayList<>();
            dirs.filter(Files::isDirectory)
                    .sorted()
                    .forEach(dir -> {
                        String name = dir.getFileName().toString();
                        TurLuceneVectorIndex index = openIndex(basePath, name);
                        long chunkCount = safeNumDocs(index);
                        long documentCount = safeDistinctDocumentCount(index);
                        if (documentCount == 0) {
                            documentCount = chunkCount;
                        }
                        result.add(new TurStoreCollectionInfo(name, name, documentCount, chunkCount));
                    });
            return result;
        } catch (IOException e) {
            log.warn("[Lucene] Failed to list collections at {}: {}", basePath, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void createCollection(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        Path basePath = resolveBasePath(turStoreInstance, options);
        openIndex(basePath, collectionName);
        log.info("[Lucene] Created collection '{}' at {}", collectionName, basePath);
    }

    @Override
    public void clearCollection(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        Path basePath = resolveBasePath(turStoreInstance, options);
        TurLuceneVectorIndex index = openIndex(basePath, collectionName);
        try {
            index.clear();
            log.info("[Lucene] Cleared collection '{}'", collectionName);
        } catch (IOException e) {
            throw new TurRuntimeException("Failed to clear Lucene collection: " + collectionName, e);
        }
    }

    @Override
    public void deleteCollection(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        Path basePath = resolveBasePath(turStoreInstance, options);
        String cacheKey = cacheKey(basePath, collectionName);
        TurLuceneVectorIndex cached = indexCache.remove(cacheKey);
        if (cached != null) {
            cached.close();
        }
        Path collectionPath = basePath.resolve(collectionName);
        if (!Files.exists(collectionPath)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(collectionPath)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("[Lucene] Failed to delete {}: {}", path, e.getMessage());
                        }
                    });
            log.info("[Lucene] Deleted collection '{}' at {}", collectionName, collectionPath);
        } catch (IOException e) {
            throw new TurRuntimeException("Failed to delete Lucene collection: " + collectionName, e);
        }
    }

    @Override
    public void deleteByMetadata(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName,
            String metadataKey,
            String metadataValue) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        Path basePath = resolveBasePath(turStoreInstance, options);
        TurLuceneVectorIndex index = openIndex(basePath, collectionName);
        try {
            org.apache.lucene.index.Term term = new org.apache.lucene.index.Term(
                    TurLuceneVectorStore.METADATA_FIELD_PREFIX + metadataKey,
                    metadataValue);
            index.getWriter().deleteDocuments(term);
            index.getWriter().commit();
            log.info("[Lucene] Deleted embeddings where {}={} from collection '{}'",
                    metadataKey, metadataValue, collectionName);
        } catch (IOException e) {
            log.warn("[Lucene] Failed to delete by metadata {}={} in '{}': {}",
                    metadataKey, metadataValue, collectionName, e.getMessage());
        }
    }

    @Override
    public Map<String, String> getSystemInfo(TurStoreInstance turStoreInstance,
            String decryptedCredential) {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("store", "Lucene");
        info.put("version", org.apache.lucene.util.Version.LATEST.toString());
        try {
            Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
            Path basePath = resolveBasePath(turStoreInstance, options);
            info.put("basePath", basePath.toAbsolutePath().toString());
            info.put("status", Files.isDirectory(basePath) || !Files.exists(basePath) ? "UP" : "DOWN");
        } catch (Exception e) {
            info.put("status", "DOWN");
            info.put("error", e.getMessage());
        }
        return info;
    }

    @Override
    public TurStoreChunkPage listChunks(TurStoreInstance turStoreInstance,
            String decryptedCredential,
            String collectionName,
            int page,
            int pageSize,
            Map<String, List<String>> filters) {
        Map<String, Object> options = optionsParser.parse(turStoreInstance.getProviderOptionsJson());
        Path basePath = resolveBasePath(turStoreInstance, options);
        TurLuceneVectorIndex index = openIndex(basePath, collectionName);

        int safePageSize = Math.clamp(pageSize, 1, 200);
        int safePage = Math.max(0, page);
        int offset = safePage * safePageSize;
        Query filter = buildFilterQuery(filters);

        try {
            TurLuceneVectorIndex.ChunkPage indexPage = index.listChunks(filter, offset, safePageSize);
            List<TurStoreChunkPage.Chunk> chunks = new ArrayList<>(indexPage.chunks().size());
            for (var stored : indexPage.chunks()) {
                Map<String, Object> metadata = TurLuceneVectorStore.parseMetadata(stored.metadataJson());
                chunks.add(new TurStoreChunkPage.Chunk(
                        stored.id(),
                        stored.content() == null ? "" : stored.content(),
                        metadata));
            }
            return new TurStoreChunkPage(chunks, indexPage.total());
        } catch (Exception e) {
            log.warn("[Lucene] Failed to list chunks for collection '{}' (page={}, pageSize={}): {}",
                    collectionName, page, pageSize, e.getMessage());
            return new TurStoreChunkPage(List.of(), -1L);
        }
    }

    /**
     * Translates the {@code Map<String, List<String>>} convention used by the
     * ANN search endpoint into a Lucene {@link BooleanQuery}: keys joined
     * with {@code AND}, multi-value lists with {@code SHOULD} (≥1 must
     * match). Returns {@code null} when no filter applies — caller passes
     * {@code null} so the index does a {@code MatchAllDocsQuery}.
     */
    private Query buildFilterQuery(Map<String, List<String>> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        BooleanQuery.Builder andBuilder = new BooleanQuery.Builder();
        boolean anyClause = false;
        for (Map.Entry<String, List<String>> e : filters.entrySet()) {
            String key = e.getKey();
            List<String> values = e.getValue();
            if (key == null || key.isBlank() || values == null || values.isEmpty()) {
                continue;
            }
            List<String> nonBlank = values.stream().filter(v -> v != null && !v.isBlank()).toList();
            if (nonBlank.isEmpty()) {
                continue;
            }
            String field = TurLuceneVectorStore.METADATA_FIELD_PREFIX + key;
            if (nonBlank.size() == 1) {
                andBuilder.add(new TermQuery(new Term(field, nonBlank.get(0))), BooleanClause.Occur.MUST);
            } else {
                BooleanQuery.Builder orBuilder = new BooleanQuery.Builder();
                for (String v : nonBlank) {
                    orBuilder.add(new TermQuery(new Term(field, v)), BooleanClause.Occur.SHOULD);
                }
                orBuilder.setMinimumNumberShouldMatch(1);
                andBuilder.add(orBuilder.build(), BooleanClause.Occur.MUST);
            }
            anyClause = true;
        }
        return anyClause ? andBuilder.build() : null;
    }

    private TurLuceneVectorIndex openIndex(Path basePath, String collectionName) {
        String key = cacheKey(basePath, collectionName);
        return indexCache.computeIfAbsent(key, k -> {
            Path indexPath = basePath.resolve(collectionName);
            try {
                Files.createDirectories(indexPath);
                log.info("[Lucene] Opening vector index at {}", indexPath);
                return new TurLuceneVectorIndex(indexPath);
            } catch (IOException e) {
                throw new TurRuntimeException("Failed to open Lucene vector index at " + indexPath, e);
            }
        });
    }

    private long safeNumDocs(TurLuceneVectorIndex index) {
        try {
            return index.numDocs();
        } catch (Exception e) {
            log.debug("[Lucene] Could not read numDocs: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Counts distinct logical documents using {@link #DOCUMENT_IDENTIFIER_KEYS}
     * as a COALESCE chain — mirrors the semantics used by the PGVector
     * provider so the React collections UI shows comparable numbers.
     * <p>
     * Strategy:
     * <ol>
     *   <li>For each candidate key, try Lucene's terms dictionary on the
     *       indexed {@code meta_<key>} field — this is exact and O(n) over
     *       distinct values, and works even when {@code metadata_json} is
     *       absent or malformed (the case that previously made the
     *       collections page fall back to {@code chunkCount}).</li>
     *   <li>If no candidate key has any indexed terms (e.g., legacy chunks
     *       indexed before the meta-field columns existed), fall back to
     *       parsing the stored {@code metadata_json} of every chunk.</li>
     * </ol>
     */
    private long safeDistinctDocumentCount(TurLuceneVectorIndex index) {
        for (String key : DOCUMENT_IDENTIFIER_KEYS) {
            try {
                long count = index.distinctTermCount(TurLuceneVectorStore.METADATA_FIELD_PREFIX + key);
                if (count > 0) {
                    return count;
                }
            } catch (Exception e) {
                log.debug("[Lucene] distinctTermCount failed for meta_{}: {}", key, e.getMessage());
            }
        }
        Set<String> identifiers = new HashSet<>();
        try {
            index.visitStoredField(TurLuceneVectorStore.FIELD_METADATA_JSON, json -> {
                Map<String, Object> metadata = TurLuceneVectorStore.parseMetadata(json);
                if (metadata.isEmpty()) {
                    return;
                }
                for (String key : DOCUMENT_IDENTIFIER_KEYS) {
                    Object value = metadata.get(key);
                    if (value != null) {
                        String asString = String.valueOf(value);
                        if (!asString.isBlank()) {
                            identifiers.add(asString);
                            return;
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.debug("[Lucene] Could not compute distinct document count: {}", e.getMessage());
        }
        return identifiers.size();
    }

    private Path resolveBasePath(TurStoreInstance turStoreInstance, Map<String, Object> options) {
        String raw = firstNonBlank(
                optionsParser.stringValue(options, BASE_PATH_OPTION),
                turStoreInstance.getUrl(),
                DEFAULT_BASE_PATH);
        Path basePath = Path.of(raw).toAbsolutePath().normalize();
        // T270 / §XIV.4.4 — resolve under a per-tenant subdirectory so two tenants'
        // same-named collections never share a Lucene directory (and the index
        // cache key, derived from basePath, is tenant-distinct). No-op (no extra
        // segment) when tenancy is off or the tenant is DEFAULT.
        String tenantSegment = tenantSegment();
        return tenantSegment.isEmpty() ? basePath : basePath.resolve(tenantSegment).normalize();
    }

    private String tenantSegment() {
        if (tenantContext == null || !tenantContext.isTenancyEnabled()) {
            return "";
        }
        String tenant = tenantContext.resolveCurrentTenant();
        if (tenant == null
                || com.viglet.turing.persistence.model.tenant.TurTenant.DEFAULT_TENANT_ID.equals(tenant)) {
            return "";
        }
        return tenant;
    }

    private String resolveCollectionName(TurStoreInstance turStoreInstance,
            Map<String, Object> options,
            String collectionNameOverride) {
        if (StringUtils.hasText(collectionNameOverride)) {
            return sanitize(collectionNameOverride);
        }
        return sanitize(firstNonBlank(
                optionsParser.stringValue(options, COLLECTION_NAME_OPTION),
                turStoreInstance.getCollectionName(),
                DEFAULT_COLLECTION));
    }

    private String cacheKey(Path basePath, String collectionName) {
        return basePath.toString().toLowerCase(Locale.ROOT) + "|" + collectionName;
    }

    /**
     * Replaces characters invalid on common filesystems. Keeps ASCII letters, digits, dash,
     * underscore and dot; everything else becomes {@code _}.
     */
    private String sanitize(String name) {
        if (name == null) {
            return DEFAULT_COLLECTION;
        }
        String sanitized = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? DEFAULT_COLLECTION : sanitized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return DEFAULT_BASE_PATH;
    }
}
