/*
 * Copyright (C) 2016-2026 the original author or authors.
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
package com.viglet.turing.api.page;

import com.viglet.turing.service.storage.TurStorageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * API for managing SPA (Single Page Application) sites hosted in MinIO.
 * Supports uploading ZIP files that are extracted into MinIO,
 * listing deployed sites, and deleting them.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@RestController
@RequestMapping("/api/page")
@Tag(name = "Pages", description = "SPA site management API")
public class TurPageAPI {

    private static final String PAGES_PREFIX = "public/";

    private final TurStorageService storageService;

    public TurPageAPI(TurStorageService storageService) {
        this.storageService = storageService;
    }

    public record TurPageSite(String name) {}

    @GetMapping
    public ResponseEntity<List<TurPageSite>> list() {
        if (!storageService.isEnabled()) {
            return ResponseEntity.ok(List.of());
        }
        List<TurPageSite> sites = storageService.listObjects(PAGES_PREFIX).stream()
                .filter(item -> item.directory())
                .map(item -> {
                    String name = item.name();
                    if (name.startsWith(PAGES_PREFIX)) {
                        name = name.substring(PAGES_PREFIX.length());
                    }
                    if (name.endsWith("/")) {
                        name = name.substring(0, name.length() - 1);
                    }
                    return new TurPageSite(name);
                })
                .filter(site -> !site.name().isBlank())
                .toList();
        return ResponseEntity.ok(sites);
    }

    @PostMapping
    public ResponseEntity<TurPageSite> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("siteName") String siteName) {
        if (!storageService.isEnabled()) {
            return ResponseEntity.badRequest().build();
        }
        String normalizedName = siteName.trim().replaceAll("[^a-zA-Z0-9._-]", "-").toLowerCase();
        if (normalizedName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String prefix = PAGES_PREFIX + normalizedName + "/";
        // Delete existing site contents before uploading
        storageService.deleteObjectsWithPrefix(prefix);
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                String entryName = entry.getName();
                // Skip hidden files and __MACOSX
                if (entryName.startsWith("__MACOSX") || entryName.contains("/.")
                        || entryName.startsWith(".")) {
                    zis.closeEntry();
                    continue;
                }
                String objectName = prefix + entryName;
                String contentType = storageService.guessContentType(entryName);
                byte[] data = zis.readAllBytes();
                storageService.uploadStream(objectName, new java.io.ByteArrayInputStream(data),
                        data.length, contentType);
                log.debug("Uploaded page file: {}", objectName);
                zis.closeEntry();
            }
        } catch (IOException e) {
            log.error("Failed to extract ZIP for site '{}'", normalizedName, e);
            return ResponseEntity.internalServerError().build();
        }
        log.info("SPA site '{}' deployed successfully to MinIO.", normalizedName);
        return ResponseEntity.ok(new TurPageSite(normalizedName));
    }

    @DeleteMapping("/{siteName}")
    public ResponseEntity<Void> delete(@PathVariable String siteName) {
        if (!storageService.isEnabled()) {
            return ResponseEntity.badRequest().build();
        }
        String prefix = PAGES_PREFIX + siteName + "/";
        storageService.deleteObjectsWithPrefix(prefix);
        log.info("SPA site '{}' deleted from MinIO.", siteName);
        return ResponseEntity.noContent().build();
    }
}
