/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

/**
 * Filesystem-backed storage implementation. Stores objects under a configurable
 * base directory ({@code viglet.storage.filesystem.path}, default
 * {@code ./store/assets}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletFilesystemStorageService implements VigletStorageService {

    private static final Logger log = LoggerFactory.getLogger(VigletFilesystemStorageService.class);
    private static final String STORAGE_NOT_INITIALIZED = "Filesystem storage is not initialized.";

    private final VigletStorageProperties properties;
    private Path basePath;

    public VigletFilesystemStorageService(VigletStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    @PostConstruct
    public void init() {
        String dir = (properties != null && properties.getFilesystem() != null
                && properties.getFilesystem().getPath() != null)
                ? properties.getFilesystem().getPath() : "./store/assets";
        this.basePath = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
            log.info("Filesystem storage initialized at: {}", basePath);
        } catch (IOException e) {
            log.error("Failed to create filesystem storage directory: {}", basePath, e);
        }
    }

    @Override
    public VigletStorageType getType() {
        return VigletStorageType.FILESYSTEM;
    }

    @Override
    public boolean isEnabled() {
        return basePath != null;
    }

    @Override
    public List<VigletStorageObject> listObjects(String prefix) {
        requireEnabled();
        String normalizedPrefix = (prefix == null || prefix.isBlank()) ? "" : prefix;
        Path dir = resolveSafe(normalizedPrefix);
        if (!Files.isDirectory(dir)) return List.of();

        List<VigletStorageObject> items = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String objectName = toObjectName(entry);
                if (Files.isDirectory(entry)) {
                    items.add(new VigletStorageObject(objectName + "/", 0, "", "", true));
                } else {
                    BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                    items.add(new VigletStorageObject(
                            objectName,
                            attrs.size(),
                            VigletStorageContentTypes.guessContentType(objectName),
                            attrs.lastModifiedTime().toString(),
                            false));
                }
            }
        } catch (IOException e) {
            log.error("Error listing filesystem objects with prefix '{}'", normalizedPrefix, e);
        }
        return items;
    }

    @Override
    public List<VigletStorageObject> listAllObjects() {
        requireEnabled();
        List<VigletStorageObject> items = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(basePath)) {
            walk.filter(Files::isRegularFile).forEach(entry -> {
                try {
                    String objectName = toObjectName(entry);
                    BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class);
                    items.add(new VigletStorageObject(
                            objectName,
                            attrs.size(),
                            VigletStorageContentTypes.guessContentType(objectName),
                            attrs.lastModifiedTime().toString(),
                            false));
                } catch (IOException e) {
                    log.error("Error reading attributes for: {}", entry, e);
                }
            });
        } catch (IOException e) {
            log.error("Error walking filesystem storage", e);
        }
        return items;
    }

    @Override
    public InputStream downloadObject(String objectName) {
        requireEnabled();
        Path target = resolveSafe(objectName);
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to download object: " + objectName, e);
        }
    }

    @Override
    public VigletStorageObjectStat statObject(String objectName) {
        requireEnabled();
        Path target = resolveSafe(objectName);
        try {
            BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);
            return new VigletStorageObjectStat(
                    objectName,
                    attrs.size(),
                    VigletStorageContentTypes.guessContentType(objectName),
                    attrs.lastModifiedTime().toString());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to stat object: " + objectName, e);
        }
    }

    @Override
    public void createFolder(String folderPath) {
        requireEnabled();
        Path target = resolveSafe(folderPath);
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create folder: " + folderPath, e);
        }
    }

    @Override
    public void uploadStream(String objectName, InputStream inputStream, long size, String contentType) {
        requireEnabled();
        Path target = resolveSafe(objectName);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to upload stream: " + objectName, e);
        }
    }

    @Override
    public void deleteObjectsWithPrefix(String prefix) {
        requireEnabled();
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        Path dir = resolveSafe(normalizedPrefix);
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> walk = Files.walk(dir).sorted(Comparator.reverseOrder())) {
            walk.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.error("Error deleting filesystem object: {}", p, e);
                }
            });
        } catch (IOException e) {
            log.error("Error deleting filesystem objects with prefix '{}'", prefix, e);
        }
    }

    @Override
    public void deleteObject(String objectName) {
        requireEnabled();
        Path target = resolveSafe(objectName);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete object: " + objectName, e);
        }
    }

    private Path resolveSafe(String objectName) {
        Path resolved = basePath.resolve(objectName).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("Path traversal attempt detected: " + objectName);
        }
        return resolved;
    }

    private String toObjectName(Path path) {
        return basePath.relativize(path).toString().replace('\\', '/');
    }

    private void requireEnabled() {
        if (basePath == null) throw new IllegalStateException(STORAGE_NOT_INITIALIZED);
    }
}
