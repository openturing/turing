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

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.service.storage.TurStorageType;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Synchronizes Lucene index segments to/from object storage (MinIO or filesystem).
 * <p>
 * Strategy: <b>Segment Shipping</b> — Lucene segments are immutable after commit,
 * making them ideal for incremental sync. Only new/changed files are uploaded;
 * orphaned remote files (from segment merges) are cleaned up.
 * <p>
 * The {@code segments_N} file is always uploaded last to ensure consistency:
 * a warm-up that reads from storage will only see a commit point that references
 * segments already present in storage.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Component
public class TurLuceneStorageSync {

    private static final String PREFIX = "lucene-indexes/";
    private static final String WRITE_LOCK = "write.lock";

    private final TurStorageService storageService;
    private final ConcurrentHashMap<String, CompletableFuture<Void>> pendingSyncs = new ConcurrentHashMap<>();
    private final ExecutorService syncExecutor = Executors.newFixedThreadPool(2);

    public TurLuceneStorageSync(TurStorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Whether storage sync is active.
     */
    public boolean isEnabled() {
        return storageService.isEnabled() && storageService.getType() != TurStorageType.NONE;
    }

    // -------------------------------------------------------------------------
    // Async entry point (called after writer.commit)
    // -------------------------------------------------------------------------

    /**
     * Schedules an async sync to storage. Only one sync per core runs at a time;
     * if a new sync is requested while the previous is still running, it queues
     * after the current one completes.
     */
    public void syncToStorageAsync(Path indexPath, String coreName) {
        if (!isEnabled()) return;
        pendingSyncs.compute(coreName, (key, existing) -> {
            CompletableFuture<Void> prev = existing != null ? existing : CompletableFuture.completedFuture(null);
            return prev.thenRunAsync(() -> syncToStorage(indexPath, coreName), syncExecutor);
        });
    }

    // -------------------------------------------------------------------------
    // Sync TO storage (upload after commit)
    // -------------------------------------------------------------------------

    /**
     * Uploads new/changed segments to storage. Deletes orphaned remote files.
     * The {@code segments_N} file is uploaded last for consistency.
     */
    public void syncToStorage(Path indexPath, String coreName) {
        if (!isEnabled()) return;
        try {
            String prefix = storagePrefix(coreName);
            Map<String, Long> localFiles = listLocalFiles(indexPath);
            Map<String, Long> remoteFiles = listRemoteFiles(prefix);

            // Upload new or changed files (segments first, segments_N last)
            String segmentsFile = null;
            for (var entry : localFiles.entrySet()) {
                String fileName = entry.getKey();
                long localSize = entry.getValue();

                if (fileName.startsWith("segments")) {
                    segmentsFile = fileName;
                    continue; // defer to last
                }

                Long remoteSize = remoteFiles.get(fileName);
                if (remoteSize == null || remoteSize != localSize) {
                    uploadFile(indexPath, prefix, fileName);
                }
            }
            // Upload segments_N last (commit point)
            if (segmentsFile != null) {
                uploadFile(indexPath, prefix, segmentsFile);
            }

            // Delete orphaned remote files (merged/deleted segments)
            for (String remoteName : remoteFiles.keySet()) {
                if (!localFiles.containsKey(remoteName)) {
                    storageService.deleteObject(prefix + remoteName);
                    log.debug("[LuceneSync] Deleted orphaned remote file: {}{}", prefix, remoteName);
                }
            }

            log.info("[LuceneSync] Synced to storage: core='{}', uploaded={}, cleaned={}",
                    coreName,
                    localFiles.size() - remoteFiles.entrySet().stream()
                            .filter(e -> localFiles.containsKey(e.getKey())
                                    && localFiles.get(e.getKey()).equals(e.getValue()))
                            .count(),
                    remoteFiles.keySet().stream().filter(k -> !localFiles.containsKey(k)).count());

        } catch (Exception e) {
            log.warn("[LuceneSync] Failed to sync to storage for core '{}': {}", coreName, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Sync FROM storage (warm-up download)
    // -------------------------------------------------------------------------

    /**
     * Downloads missing segments from storage to local disk.
     * Compares by name + size; skips files already present with matching size.
     */
    public void syncFromStorage(Path indexPath, String coreName) {
        if (!isEnabled()) return;
        try {
            String prefix = storagePrefix(coreName);
            Map<String, Long> remoteFiles = listRemoteFiles(prefix);

            if (remoteFiles.isEmpty()) {
                log.debug("[LuceneSync] No remote files for core '{}'", coreName);
                return;
            }

            Files.createDirectories(indexPath);
            Map<String, Long> localFiles = listLocalFiles(indexPath);

            int downloaded = 0;
            for (var entry : remoteFiles.entrySet()) {
                String fileName = entry.getKey();
                long remoteSize = entry.getValue();
                Long localSize = localFiles.get(fileName);

                if (localSize != null && localSize == remoteSize) {
                    continue; // already up to date
                }

                Path targetFile = indexPath.resolve(fileName);
                try (InputStream in = storageService.downloadObject(prefix + fileName);
                     OutputStream out = Files.newOutputStream(targetFile)) {
                    in.transferTo(out);
                    downloaded++;
                    log.debug("[LuceneSync] Downloaded: {} ({} bytes)", fileName, remoteSize);
                }
            }

            log.info("[LuceneSync] Warm-up from storage: core='{}', downloaded={}/{} files",
                    coreName, downloaded, remoteFiles.size());

        } catch (Exception e) {
            log.warn("[LuceneSync] Failed warm-up for core '{}': {}", coreName, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Delete from storage
    // -------------------------------------------------------------------------

    /**
     * Deletes all storage objects for a core.
     */
    public void deleteFromStorage(String coreName) {
        if (!isEnabled()) return;
        try {
            storageService.deleteObjectsWithPrefix(storagePrefix(coreName));
            log.info("[LuceneSync] Deleted storage for core: {}", coreName);
        } catch (Exception e) {
            log.warn("[LuceneSync] Failed to delete storage for core '{}': {}", coreName, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String storagePrefix(String coreName) {
        return PREFIX + coreName + "/";
    }

    private Map<String, Long> listLocalFiles(Path indexPath) throws IOException {
        Map<String, Long> files = new LinkedHashMap<>();
        if (!Files.isDirectory(indexPath)) return files;
        try (Stream<Path> stream = Files.list(indexPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> shouldSync(p.getFileName().toString()))
                    .forEach(p -> {
                        try {
                            files.put(p.getFileName().toString(), Files.size(p));
                        } catch (IOException e) {
                            log.warn("[LuceneSync] Could not read size of {}", p);
                        }
                    });
        }
        return files;
    }

    private Map<String, Long> listRemoteFiles(String prefix) {
        Map<String, Long> files = new LinkedHashMap<>();
        for (TurAssetItem item : storageService.listObjects(prefix)) {
            if (item.directory()) continue;
            String name = item.name();
            // Strip prefix to get just the filename
            if (name.startsWith(prefix)) {
                name = name.substring(prefix.length());
            }
            if (name.isEmpty() || !shouldSync(name)) continue;
            files.put(name, item.size());
        }
        return files;
    }

    private void uploadFile(Path indexPath, String prefix, String fileName) {
        Path file = indexPath.resolve(fileName);
        try (InputStream in = Files.newInputStream(file)) {
            long size = Files.size(file);
            storageService.uploadStream(prefix + fileName, in, size, "application/octet-stream");
            log.debug("[LuceneSync] Uploaded: {}{} ({} bytes)", prefix, fileName, size);
        } catch (IOException e) {
            log.warn("[LuceneSync] Failed to upload {}: {}", fileName, e.getMessage());
        }
    }

    private static boolean shouldSync(String fileName) {
        return !WRITE_LOCK.equals(fileName);
    }

    @PreDestroy
    public void shutdown() {
        syncExecutor.shutdown();
        try {
            if (!syncExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                syncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            syncExecutor.shutdownNow();
        }
    }
}
