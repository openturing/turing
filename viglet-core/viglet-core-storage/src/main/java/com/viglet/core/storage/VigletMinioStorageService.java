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

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;

/**
 * MinIO-backed storage implementation.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletMinioStorageService implements VigletStorageService {

    private static final Logger log = LoggerFactory.getLogger(VigletMinioStorageService.class);
    private static final String PATH_DELIMITER = "/";
    private static final String MINIO_NOT_CONFIGURED = "MinIO is not configured.";

    private final VigletStorageProperties properties;
    private MinioClient minioClient;

    public VigletMinioStorageService(VigletStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public VigletStorageType getType() {
        return VigletStorageType.MINIO;
    }

    @Override
    public boolean isEnabled() {
        return minioClient != null;
    }

    @Override
    @PostConstruct
    public void init() {
        VigletStorageProperties.Minio minio = properties != null ? properties.getMinio() : null;
        if (minio == null || minio.getEndpoint() == null) {
            log.info("MinIO storage selected but not configured. Check viglet.storage.minio.* properties.");
            return;
        }
        this.minioClient = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
        ensureBucket(minio.getBucket());
    }

    @Override
    public List<VigletStorageObject> listObjects(String prefix) {
        if (minioClient == null) return List.of();
        String bucket = getBucket();
        String normalizedPrefix = (prefix == null || prefix.isBlank()) ? "" : prefix;
        List<VigletStorageObject> items = new ArrayList<>();
        var builder = ListObjectsArgs.builder()
                .bucket(bucket)
                .prefix(normalizedPrefix)
                .recursive(false);
        Iterable<Result<Item>> results = minioClient.listObjects(builder.build());
        for (Result<Item> result : results) {
            try {
                Item item = result.get();
                String objectName = item.objectName();
                if (item.isDir()) {
                    items.add(new VigletStorageObject(objectName, 0, "", "", true));
                } else {
                    ZonedDateTime lastModified = item.lastModified();
                    items.add(new VigletStorageObject(
                            objectName,
                            item.size(),
                            VigletStorageContentTypes.guessContentType(objectName),
                            lastModified != null ? lastModified.toString() : "",
                            false));
                }
            } catch (Exception e) {
                log.error("Error listing MinIO object", e);
            }
        }
        return items;
    }

    @Override
    public List<VigletStorageObject> listAllObjects() {
        if (minioClient == null) return List.of();
        String bucket = getBucket();
        List<VigletStorageObject> items = new ArrayList<>();
        var builder = ListObjectsArgs.builder()
                .bucket(bucket)
                .recursive(true);
        Iterable<Result<Item>> results = minioClient.listObjects(builder.build());
        for (Result<Item> result : results) {
            try {
                Item item = result.get();
                if (!item.isDir()) {
                    ZonedDateTime lastModified = item.lastModified();
                    items.add(new VigletStorageObject(
                            item.objectName(),
                            item.size(),
                            VigletStorageContentTypes.guessContentType(item.objectName()),
                            lastModified != null ? lastModified.toString() : "",
                            false));
                }
            } catch (Exception e) {
                log.error("Error listing MinIO object", e);
            }
        }
        return items;
    }

    @Override
    public InputStream downloadObject(String objectName) {
        requireEnabled();
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(getBucket())
                            .object(objectName)
                            .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to download object: " + objectName, e);
        }
    }

    @Override
    public VigletStorageObjectStat statObject(String objectName) {
        requireEnabled();
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(getBucket())
                            .object(objectName)
                            .build());
            return new VigletStorageObjectStat(
                    objectName,
                    stat.size(),
                    stat.contentType(),
                    stat.lastModified() != null ? stat.lastModified().toString() : "");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stat object: " + objectName, e);
        }
    }

    @Override
    public void createFolder(String folderPath) {
        requireEnabled();
        String path = folderPath.endsWith(PATH_DELIMITER) ? folderPath : folderPath + PATH_DELIMITER;
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(getBucket())
                            .object(path)
                            .stream(InputStream.nullInputStream(), 0L, -1L)
                            .contentType("application/x-directory")
                            .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create folder: " + path, e);
        }
    }

    @Override
    public void uploadStream(String objectName, InputStream inputStream, long size, String contentType) {
        requireEnabled();
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(getBucket())
                            .object(objectName)
                            .stream(inputStream, size, -1L)
                            .contentType(contentType != null ? contentType
                                    : VigletStorageContentTypes.DEFAULT_CONTENT_TYPE)
                            .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload stream: " + objectName, e);
        }
    }

    @Override
    public void deleteObjectsWithPrefix(String prefix) {
        requireEnabled();
        String normalizedPrefix = prefix.endsWith(PATH_DELIMITER) ? prefix : prefix + PATH_DELIMITER;
        var builder = ListObjectsArgs.builder()
                .bucket(getBucket())
                .prefix(normalizedPrefix)
                .recursive(true);
        for (Result<Item> result : minioClient.listObjects(builder.build())) {
            try {
                deleteObject(result.get().objectName());
            } catch (Exception e) {
                log.error("Error deleting MinIO object during prefix cleanup", e);
            }
        }
    }

    @Override
    public void deleteObject(String objectName) {
        requireEnabled();
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(getBucket())
                            .object(objectName)
                            .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to delete object: " + objectName, e);
        }
    }

    private void requireEnabled() {
        if (minioClient == null) throw new IllegalStateException(MINIO_NOT_CONFIGURED);
    }

    private String getBucket() {
        return properties.getMinio().getBucket();
    }

    private void ensureBucket(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.error("Failed to ensure MinIO bucket '{}' exists", bucket, e);
        }
    }
}
