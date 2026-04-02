package com.viglet.turing.service.storage;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurMinioProperty;
import io.minio.*;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MinIO-backed storage implementation.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public class TurMinioStorageService implements TurStorageService {

    private static final Logger log = LoggerFactory.getLogger(TurMinioStorageService.class);
    private static final String PATH_DELIMITER = "/";
    private static final String MINIO_NOT_CONFIGURED = "MinIO is not configured.";

    private final TurConfigProperties configProperties;
    private MinioClient minioClient;

    public TurMinioStorageService(TurConfigProperties configProperties) {
        this.configProperties = configProperties;
    }

    @Override
    public TurStorageType getType() {
        return TurStorageType.MINIO;
    }

    @Override
    public boolean isEnabled() {
        return minioClient != null;
    }

    @PostConstruct
    void init() {
        TurMinioProperty minio = configProperties.getStorage() != null
                ? configProperties.getStorage().getMinio() : null;
        if (minio == null || minio.getEndpoint() == null) {
            log.info("MinIO storage selected but not configured. Check turing.storage.minio.* properties.");
            return;
        }
        this.minioClient = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
        ensureBucket(minio.getBucket());
    }

    @Override
    public List<TurAssetItem> listObjects(String prefix) {
        if (minioClient == null) return List.of();
        String bucket = getBucket();
        String normalizedPrefix = (prefix == null || prefix.isBlank()) ? "" : prefix;
        List<TurAssetItem> items = new ArrayList<>();
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
                    items.add(new TurAssetItem(objectName, 0, "", "", true));
                } else {
                    ZonedDateTime lastModified = item.lastModified();
                    items.add(new TurAssetItem(
                            objectName,
                            item.size(),
                            TurStorageContentTypes.guessContentType(objectName),
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
    public List<TurAssetItem> listAllObjects() {
        if (minioClient == null) return List.of();
        String bucket = getBucket();
        List<TurAssetItem> items = new ArrayList<>();
        var builder = ListObjectsArgs.builder()
                .bucket(bucket)
                .recursive(true);
        Iterable<Result<Item>> results = minioClient.listObjects(builder.build());
        for (Result<Item> result : results) {
            try {
                Item item = result.get();
                if (!item.isDir()) {
                    ZonedDateTime lastModified = item.lastModified();
                    items.add(new TurAssetItem(
                            item.objectName(),
                            item.size(),
                            TurStorageContentTypes.guessContentType(item.objectName()),
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
    public TurStorageObjectStat statObject(String objectName) {
        requireEnabled();
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(getBucket())
                            .object(objectName)
                            .build());
            return new TurStorageObjectStat(
                    objectName,
                    stat.size(),
                    stat.contentType(),
                    stat.lastModified() != null ? stat.lastModified().toString() : "");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to stat object: " + objectName, e);
        }
    }

    @Override
    public void uploadObject(MultipartFile file, String prefix) {
        requireEnabled();
        String objectName = (prefix != null && !prefix.isBlank() ? prefix : "") + file.getOriginalFilename();
        try (InputStream is = file.getInputStream()) {
            String contentType = file.getContentType() != null
                    ? file.getContentType()
                    : TurStorageContentTypes.DEFAULT_CONTENT_TYPE;
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(getBucket())
                            .object(objectName)
                            .stream(is, file.getSize(), -1L)
                            .contentType(contentType)
                            .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to upload object: " + objectName, e);
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
                            .contentType(contentType != null ? contentType : TurStorageContentTypes.DEFAULT_CONTENT_TYPE)
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
        return configProperties.getStorage().getMinio().getBucket();
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
