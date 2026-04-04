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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.service.storage.TurStorageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
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
    private static final String MANIFEST_FILE = "turing-manifest.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurStorageService storageService;

    public TurPageAPI(TurStorageService storageService) {
        this.storageService = storageService;
    }

    public record TurPageManifest(
            String name,
            String version,
            String author,
            String repository,
            String description,
            String buildDate,
            String snSite,
            String locale,
            String framework,
            String buildTool
    ) {}

    public record TurPageSite(String name, TurPageManifest manifest) {}

    @GetMapping
    public ResponseEntity<List<TurPageSite>> list() {
        if (!storageService.isEnabled()) {
            return ResponseEntity.ok(List.of());
        }
        List<TurPageSite> sites = storageService.listObjects(PAGES_PREFIX).stream()
                .filter(item -> item.directory())
                .map(item -> {
                    String dirName = item.name();
                    if (dirName.startsWith(PAGES_PREFIX)) {
                        dirName = dirName.substring(PAGES_PREFIX.length());
                    }
                    if (dirName.endsWith("/")) {
                        dirName = dirName.substring(0, dirName.length() - 1);
                    }
                    return new TurPageSite(dirName, readManifestFromStorage(dirName));
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
        TurPageManifest manifest = null;
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
                byte[] data = zis.readAllBytes();
                // Parse manifest if found
                if (entryName.equals(MANIFEST_FILE) || entryName.endsWith("/" + MANIFEST_FILE)) {
                    manifest = parseManifest(data);
                }
                String objectName = prefix + entryName;
                String contentType = storageService.guessContentType(entryName);
                storageService.uploadStream(objectName, new ByteArrayInputStream(data),
                        data.length, contentType);
                log.debug("Uploaded page file: {}", objectName);
                zis.closeEntry();
            }
        } catch (IOException e) {
            log.error("Failed to extract ZIP for site '{}'", normalizedName, e);
            return ResponseEntity.internalServerError().build();
        }
        log.info("SPA site '{}' deployed successfully.", normalizedName);
        return ResponseEntity.ok(new TurPageSite(normalizedName, manifest));
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

    private TurPageManifest readManifestFromStorage(String siteName) {
        String manifestObject = PAGES_PREFIX + siteName + "/" + MANIFEST_FILE;
        try (InputStream is = storageService.downloadObject(manifestObject)) {
            if (is == null) {
                return null;
            }
            return parseManifest(is.readAllBytes());
        } catch (Exception e) {
            log.debug("No manifest found for site '{}'", siteName);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private TurPageManifest parseManifest(byte[] data) {
        try {
            Map<String, Object> json = OBJECT_MAPPER.readValue(data, Map.class);
            return new TurPageManifest(
                    str(json, "name"),
                    str(json, "version"),
                    str(json, "author"),
                    str(json, "repository"),
                    str(json, "description"),
                    str(json, "buildDate"),
                    str(json, "snSite"),
                    str(json, "locale"),
                    str(json, "framework"),
                    str(json, "buildTool")
            );
        } catch (IOException e) {
            log.warn("Failed to parse turing-manifest.json: {}", e.getMessage());
            return null;
        }
    }

    private static String str(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
