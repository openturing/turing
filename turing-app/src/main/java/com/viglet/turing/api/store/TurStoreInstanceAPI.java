/*
 * Copyright (C) 2016-2022 the original author or authors.
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
package com.viglet.turing.api.store;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProviderFactory;
import com.viglet.turing.genai.provider.store.TurStoreCollectionInfo;
import com.viglet.turing.genai.provider.store.TurStoreExportRecord;
import com.viglet.turing.genai.provider.store.TurStoreImportResult;
import com.viglet.turing.persistence.dto.store.TurStoreInstanceDto;
import com.viglet.turing.persistence.mapper.store.TurStoreInstanceMapper;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;
import com.viglet.turing.tenant.TurInfraTenantScope;

import org.springframework.ai.embedding.EmbeddingModel;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/store")
@Tag(name = "Embedding Store", description = "Embedding Store API")
public class TurStoreInstanceAPI {
    private final TurStoreInstanceRepository turStoreInstanceRepository;
    private final TurStoreInstanceMapper turStoreInstanceMapper;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurGenAiStoreProviderFactory storeProviderFactory;
    private final TurEmbeddingModelRepository embeddingModelRepository;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurRagContextBuilder ragContextBuilder;
    private final TurInfraTenantScope tenantScope;
    private final com.viglet.turing.genai.batch.embedding.TurBatchReEmbeddingService batchReEmbeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TurStoreInstanceAPI(TurStoreInstanceRepository turStoreInstanceRepository,
            TurStoreInstanceMapper turStoreInstanceMapper,
            TurSecretCryptoService turSecretCryptoService,
            TurGenAiStoreProviderFactory storeProviderFactory,
            TurEmbeddingModelRepository embeddingModelRepository,
            TurGlobalSettingsService globalSettingsService,
            TurRagContextBuilder ragContextBuilder,
            TurInfraTenantScope tenantScope,
            com.viglet.turing.genai.batch.embedding.TurBatchReEmbeddingService batchReEmbeddingService) {
        this.turStoreInstanceRepository = turStoreInstanceRepository;
        this.turStoreInstanceMapper = turStoreInstanceMapper;
        this.turSecretCryptoService = turSecretCryptoService;
        this.storeProviderFactory = storeProviderFactory;
        this.embeddingModelRepository = embeddingModelRepository;
        this.globalSettingsService = globalSettingsService;
        this.ragContextBuilder = ragContextBuilder;
        this.tenantScope = tenantScope;
        this.batchReEmbeddingService = batchReEmbeddingService;
    }

    @Operation(summary = "Embedding Store List")
    @GetMapping
    @Secured({"ROLE_ADMIN", "STORE_VIEW"})
    public List<TurStoreInstanceDto> turStoreInstanceList() {
        java.util.Comparator<TurStoreInstance> byTitle =
                java.util.Comparator.comparing(TurStoreInstance::getTitle, String.CASE_INSENSITIVE_ORDER);
        return turStoreInstanceMapper.toDtoList(tenantScope.visibleList(
                () -> this.turStoreInstanceRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()),
                tenantId -> this.turStoreInstanceRepository.findVisibleToTenant(tenantId)
                        .stream().sorted(byTitle).toList()));
    }

    @Operation(summary = "Embedding Store structure")
    @GetMapping("/structure")
    @Secured({"ROLE_ADMIN", "STORE_VIEW"})
    public TurStoreInstanceDto turEmbeddingStoreInstanceStructure() {
        TurStoreInstance turStoreInstance = new TurStoreInstance();
        turStoreInstance.setTurStoreVendor(new TurStoreVendor());
        return turStoreInstanceMapper.toDto(turStoreInstance);

    }

    @Operation(summary = "Show a Embedding Store")
    @GetMapping("/{id}")
    @Secured({"ROLE_ADMIN", "STORE_VIEW"})
    public TurStoreInstanceDto turStoreInstanceGet(@PathVariable String id) {
        return turStoreInstanceMapper
                .toDto(this.turStoreInstanceRepository.findById(id).filter(tenantScope::isVisibleToTenant).orElse(new TurStoreInstance()));
    }

    @Operation(summary = "Update a Embedding Store")
    @PutMapping("/{id}")
    @Secured({"ROLE_ADMIN", "STORE_EDIT"})
    public TurStoreInstanceDto turStoreInstanceUpdate(@PathVariable String id,
            @RequestBody Map<String, Object> payload) {
        TurStoreInstance turStoreInstance = objectMapper.convertValue(payload, TurStoreInstance.class);
        String credential = payload.get("credential") instanceof String credentialValue ? credentialValue : null;
        return turStoreInstanceRepository.findById(id).filter(tenantScope::isVisibleToTenant).map(turStoreInstanceEdit -> {
            tenantScope.assertWritable(turStoreInstanceEdit);
            turStoreInstanceEdit.setTitle(turStoreInstance.getTitle());
            turStoreInstanceEdit.setDescription(turStoreInstance.getDescription());
            turStoreInstanceEdit.setIcon(turStoreInstance.getIcon());
            turStoreInstanceEdit.setTurStoreVendor(turStoreInstance.getTurStoreVendor());
            turStoreInstanceEdit.setUrl(turStoreInstance.getUrl());
            turStoreInstanceEdit.setEnabled(turStoreInstance.getEnabled());
            turStoreInstanceEdit.setCollectionName(turStoreInstance.getCollectionName());
            turStoreInstanceEdit.setProviderOptionsJson(turStoreInstance.getProviderOptionsJson());
            if (StringUtils.hasText(credential)) {
                turStoreInstanceEdit
                        .setCredentialEncrypted(turSecretCryptoService.encrypt(credential));
            }
            this.turStoreInstanceRepository.save(turStoreInstanceEdit);
            return turStoreInstanceMapper.toDto(turStoreInstanceEdit);
        }).orElse(new TurStoreInstanceDto());

    }

    @Transactional
    @Operation(summary = "Delete a Embedding Store")
    @DeleteMapping("/{id}")
    @Secured({"ROLE_ADMIN", "STORE_DELETE"})
    public boolean turStoreInstanceDelete(@PathVariable String id) {
        // T365 — scope the by-id delete: a non-visible id (another tenant's
        // instance) is treated as not-found, never deleted.
        this.turStoreInstanceRepository.findById(id).filter(tenantScope::isVisibleToTenant)
                .ifPresent(existing -> {
                    tenantScope.assertWritable(existing);
                    this.turStoreInstanceRepository.delete(id);
                });
        return true;
    }

    @Operation(summary = "List collections in the Embedding Store")
    @GetMapping("/{id}/collections")
    @Secured({"ROLE_ADMIN", "STORE_VIEW"})
    public List<TurStoreCollectionInfo> turStoreInstanceCollections(@PathVariable String id) {
        return turStoreInstanceRepository.findById(id).filter(tenantScope::isVisibleToTenant).map(instance -> {
            var provider = storeProviderFactory.getProvider(instance);
            String credential = turSecretCryptoService.decrypt(instance.getCredentialEncrypted());
            return provider.listCollections(instance, credential);
        }).orElse(List.of());
    }

    /**
     * On-demand distinct-document count for a single collection. Split out
     * from the listing endpoint because some backends (Chroma) compute it by
     * paginating chunk metadata, which is too expensive to run on every
     * page-load. The console fetches it lazily per row, so the table renders
     * immediately and document counts fill in afterwards.
     *
     * @since 2026.2.6
     */
    @Operation(summary = "Distinct document count for a single collection")
    @GetMapping("/{id}/collections/{collectionName}/document-count")
    @Secured({"ROLE_ADMIN", "STORE_VIEW"})
    public Map<String, Long> turStoreInstanceCollectionDocumentCount(@PathVariable String id,
            @PathVariable String collectionName) {
        return turStoreInstanceRepository.findById(id).filter(tenantScope::isVisibleToTenant).map(instance -> {
            var provider = storeProviderFactory.getProvider(instance);
            String credential = turSecretCryptoService.decrypt(instance.getCredentialEncrypted());
            long count = provider.countDocuments(instance, credential, collectionName);
            return Map.of("documentCount", count);
        }).orElse(Map.of("documentCount", -1L));
    }

    @Operation(summary = "Create a collection")
    @PostMapping("/{id}/collections/{collectionName}")
    @Secured({"ROLE_ADMIN", "STORE_CREATE"})
    public ResponseEntity<Void> createCollection(@PathVariable String id, @PathVariable String collectionName) {
        turStoreInstanceRepository.findById(id).filter(tenantScope::isVisibleToTenant).ifPresent(instance -> {
            var provider = storeProviderFactory.getProvider(instance);
            String credential = turSecretCryptoService.decrypt(instance.getCredentialEncrypted());
            provider.createCollection(instance, credential, collectionName);
        });
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Clear all embeddings in a collection")
    @DeleteMapping("/{id}/collections/{collectionName}/clear")
    @Secured({"ROLE_ADMIN", "STORE_DELETE"})
    public ResponseEntity<Void> clearCollection(@PathVariable String id, @PathVariable String collectionName) {
        turStoreInstanceRepository.findById(id).filter(tenantScope::isVisibleToTenant).ifPresent(instance -> {
            var provider = storeProviderFactory.getProvider(instance);
            String credential = turSecretCryptoService.decrypt(instance.getCredentialEncrypted());
            provider.clearCollection(instance, credential, collectionName);
        });
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete a collection")
    @DeleteMapping("/{id}/collections/{collectionName}")
    @Secured({"ROLE_ADMIN", "STORE_DELETE"})
    public ResponseEntity<Void> deleteCollection(@PathVariable String id, @PathVariable String collectionName) {
        turStoreInstanceRepository.findById(id).filter(tenantScope::isVisibleToTenant).ifPresent(instance -> {
            var provider = storeProviderFactory.getProvider(instance);
            String credential = turSecretCryptoService.decrypt(instance.getCredentialEncrypted());
            provider.deleteCollection(instance, credential, collectionName);
        });
        return ResponseEntity.ok().build();
    }

    /**
     * Imports a JSONL file (optionally wrapped in a ZIP) into a collection.
     * The file shape mirrors the export endpoint:
     * <pre>
     * { "id": "...", "content": "...", "embedding": [...], "metadata": {...} }
     * </pre>
     * Records that already carry an embedding are upserted verbatim. Records
     * with a missing/empty {@code embedding} are re-embedded by the LLM
     * resolved from {@code embeddingModelId} (or, when omitted, the Global
     * Settings default), so third-party datasets without precomputed vectors
     * can still be imported in one click.
     *
     * @since 2026.2.4
     */
    @Operation(summary = "Import JSONL into a collection")
    @PostMapping(value = "/{id}/collections/{collectionName}/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Secured({"ROLE_ADMIN", "STORE_EDIT"})
    public ResponseEntity<TurStoreImportResult> importCollection(@PathVariable String id,
            @PathVariable String collectionName,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "embeddingModelId", required = false) String embeddingModelId) {
        TurStoreInstance instance = turStoreInstanceRepository.findById(id).filter(tenantScope::isVisibleToTenant).orElse(null);
        if (instance == null || file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var provider = storeProviderFactory.getProvider(instance);
        String credential = turSecretCryptoService.decrypt(instance.getCredentialEncrypted());
        EmbeddingModel embeddingModel = resolveImportEmbeddingModel(embeddingModelId);

        try (InputStream raw = file.getInputStream();
                InputStream payload = openPayloadStream(raw, file.getOriginalFilename())) {
            if (payload == null) {
                log.warn("[Store] No JSONL entry found inside ZIP '{}'", file.getOriginalFilename());
                return ResponseEntity.badRequest().build();
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(payload, StandardCharsets.UTF_8));
            Iterable<TurStoreExportRecord> records = () -> jsonlIterator(reader);
            TurStoreImportResult result = provider.importChunks(
                    instance, credential, collectionName, records, embeddingModel);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.warn("[Store] Import failed for collection '{}': {}", collectionName, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * F.7 / §X.8.c — schedule a batch re-embedding of a collection (50% off, 24h)
     * after switching its embedding model, instead of a synchronous re-embed.
     * Reads the collection's chunks, submits their text as an OpenAI embeddings
     * Batch keyed by chunk id; the precomputed vectors are written back when the
     * batch ends. Returns the number of chunks scheduled — {@code 0} (HTTP 409)
     * when the Batch embedding tier is off or the model's vendor has no Batch
     * embeddings API, so the caller can fall back to the synchronous reindex.
     */
    @Operation(summary = "Schedule batch re-embedding of a collection")
    @PostMapping("/{id}/collections/{collectionName}/reembed-batch")
    @Secured({"ROLE_ADMIN", "STORE_EDIT"})
    public ResponseEntity<Map<String, Object>> reEmbedCollection(@PathVariable String id,
            @PathVariable String collectionName,
            @RequestParam("embeddingModelId") String embeddingModelId) {
        TurStoreInstance instance = turStoreInstanceRepository.findById(id)
                .filter(tenantScope::isVisibleToTenant).orElse(null);
        TurEmbeddingModel embeddingModel = embeddingModelRepository.findById(embeddingModelId)
                .filter(tenantScope::isVisibleToTenant).orElse(null);
        if (instance == null || embeddingModel == null) {
            return ResponseEntity.badRequest().build();
        }
        if (!batchReEmbeddingService.isAvailable(embeddingModel)) {
            return ResponseEntity.status(409).body(Map.of(
                    "scheduled", 0,
                    "reason", "Batch embedding tier disabled or vendor has no embeddings Batch API"));
        }
        int scheduled = batchReEmbeddingService.submitForCollection(instance, collectionName, embeddingModel);
        return ResponseEntity.ok(Map.of("scheduled", scheduled));
    }

    /**
     * Resolves the {@link EmbeddingModel} used to re-embed records whose
     * vector is missing in the JSONL: explicit {@code embeddingModelId} from
     * the request wins; otherwise falls back to the global default. Returns
     * {@code null} when nothing can be resolved — the provider will skip such
     * records and report them under {@code failed} in the result.
     */
    private EmbeddingModel resolveImportEmbeddingModel(String embeddingModelId) {
        String resolvedId = StringUtils.hasText(embeddingModelId)
                ? embeddingModelId
                : globalSettingsService.getDefaultEmbeddingModelId();
        if (!StringUtils.hasText(resolvedId)) {
            return null;
        }
        TurEmbeddingModel embModel = embeddingModelRepository.findById(resolvedId)
                .filter(tenantScope::isVisibleToTenant).orElse(null);
        if (embModel == null) {
            return null;
        }
        try {
            return ragContextBuilder.resolveEmbeddingModel(embModel);
        } catch (Exception e) {
            log.warn("[Store] Could not resolve embedding model '{}' for import: {}",
                    resolvedId, e.getMessage());
            return null;
        }
    }

    /**
     * Detects the upload format from the filename and exposes a JSONL stream:
     * {@code .zip} → first {@code .jsonl} / {@code .ndjson} entry inside;
     * everything else → the raw bytes (assumed to be JSONL). Returns
     * {@code null} when a ZIP has no JSONL entry — the controller treats that
     * as a 400.
     */
    private InputStream openPayloadStream(InputStream raw, String fileName) throws IOException {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!name.endsWith(".zip")) {
            return raw;
        }
        ZipInputStream zip = new ZipInputStream(raw);
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            String entryName = entry.getName().toLowerCase(Locale.ROOT);
            if (!entry.isDirectory() && (entryName.endsWith(".jsonl") || entryName.endsWith(".ndjson"))) {
                return zip;
            }
        }
        zip.close();
        return null;
    }

    /**
     * Lazy iterator over JSONL lines: parses one line at a time so the
     * request body never materializes in memory. Blank lines are skipped;
     * malformed lines are logged at WARN and yield {@code null} records,
     * which the provider counts as {@code failed}.
     */
    private java.util.Iterator<TurStoreExportRecord> jsonlIterator(BufferedReader reader) {
        TypeReference<TurStoreExportRecord> recordType = new TypeReference<>() {};
        return new java.util.Iterator<>() {
            private TurStoreExportRecord next;
            private boolean exhausted = false;

            private void advance() {
                if (exhausted || next != null) {
                    return;
                }
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.isBlank()) {
                            continue;
                        }
                        next = parseRecordOrNull(line, recordType);
                        return;
                    }
                } catch (IOException ioError) {
                    log.warn("[Store] JSONL read failed: {}", ioError.getMessage());
                }
                exhausted = true;
            }

            @Override
            public boolean hasNext() {
                advance();
                return !exhausted || next != null;
            }

            @Override
            public TurStoreExportRecord next() {
                advance();
                if (next == null && exhausted) {
                    throw new java.util.NoSuchElementException();
                }
                TurStoreExportRecord out = next;
                next = null;
                return out;
            }
        };
    }

    /**
     * Parses one JSONL line into a record, or returns {@code null} (logged at WARN) when the line
     * is malformed — the provider counts {@code null} records as failed.
     */
    private TurStoreExportRecord parseRecordOrNull(String line,
            TypeReference<TurStoreExportRecord> recordType) {
        try {
            return objectMapper.readValue(line, recordType);
        } catch (Exception parseError) {
            log.warn("[Store] Skipping malformed JSONL line: {}", parseError.getMessage());
            return null;
        }
    }

    /**
     * Streams every chunk in the collection as a single JSONL entry inside a
     * ZIP archive. JSONL keeps each row independent so consumers can stream
     * the file line-by-line; the ZIP wrapper compresses the (verbose) embedding
     * vectors and yields a single, easy-to-share artifact.
     *
     * @since 2026.2.4
     */
    @Operation(summary = "Export collection contents as JSONL inside a ZIP")
    @GetMapping(value = "/{id}/collections/{collectionName}/export", produces = "application/zip")
    @Secured({"ROLE_ADMIN", "STORE_VIEW"})
    public ResponseEntity<StreamingResponseBody> exportCollection(@PathVariable String id,
            @PathVariable String collectionName) {
        return turStoreInstanceRepository.findById(id).filter(tenantScope::isVisibleToTenant).map(instance -> {
            var provider = storeProviderFactory.getProvider(instance);
            String credential = turSecretCryptoService.decrypt(instance.getCredentialEncrypted());
            String safeName = collectionName.replaceAll("[^A-Za-z0-9._-]", "_");
            StreamingResponseBody body = output -> {
                ObjectMapper jsonl = new ObjectMapper();
                try (ZipOutputStream zip = new ZipOutputStream(output)) {
                    zip.putNextEntry(new ZipEntry(safeName + ".jsonl"));
                    Writer writer = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
                    provider.streamExport(instance, credential, collectionName, row -> {
                        try {
                            writer.write(jsonl.writeValueAsString(row));
                            writer.write('\n');
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
                    writer.flush();
                    zip.closeEntry();
                } catch (java.io.UncheckedIOException e) {
                    log.warn("[Store] Export aborted for collection '{}': {}",
                            collectionName, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                }
            };
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .header("Content-Disposition", "attachment; filename=\"" + safeName + ".jsonl.zip\"")
                    .body(body);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Get system information for an Embedding Store instance")
    @GetMapping("/{id}/system-info")
    @Secured({"ROLE_ADMIN", "STORE_VIEW"})
    public ResponseEntity<java.util.Map<String, String>> turStoreInstanceSystemInfo(@PathVariable String id) {
        return turStoreInstanceRepository.findById(id).filter(tenantScope::isVisibleToTenant).map(instance -> {
            var provider = storeProviderFactory.getProvider(instance);
            String credential = turSecretCryptoService.decrypt(instance.getCredentialEncrypted());
            java.util.Map<String, String> info = provider.getSystemInfo(instance, credential);
            return ResponseEntity.ok(info);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create a Embedding Store")
    @PostMapping
    @Secured({"ROLE_ADMIN", "STORE_CREATE"})
    public TurStoreInstanceDto turStoreInstanceAdd(@RequestBody Map<String, Object> payload) {
        TurStoreInstance turStoreInstance = objectMapper.convertValue(payload, TurStoreInstance.class);
        String credential = payload.get("credential") instanceof String credentialValue ? credentialValue : null;
        if (StringUtils.hasText(credential)) {
            turStoreInstance.setCredentialEncrypted(turSecretCryptoService.encrypt(credential));
        }
        tenantScope.stampOnCreate(turStoreInstance);
        this.turStoreInstanceRepository.save(turStoreInstance);
        return turStoreInstanceMapper.toDto(turStoreInstance);

    }
}
