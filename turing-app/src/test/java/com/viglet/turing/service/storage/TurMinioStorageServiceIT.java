/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurMinioProperty;
import com.viglet.turing.properties.TurStorageProperty;

/**
 * Integration test for {@link TurMinioStorageService} that exercises the
 * service against a real MinIO container. Runs under {@code mvn verify} via the
 * Failsafe plugin; {@code mvn test} skips it because of the {@code IT} suffix.
 *
 * <p>Requires Docker to be available locally. The {@code disabledWithoutDocker
 * = true} flag tells Testcontainers to skip the whole class (reported as
 * disabled in surefire-reports) when Docker isn't running — useful in CI
 * runners and developer machines where Docker may be absent.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Testcontainers(disabledWithoutDocker = true)
class TurMinioStorageServiceIT {

    private static final String BUCKET = "turing-it";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";

    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:latest")
            .withUserName(ACCESS_KEY)
            .withPassword(SECRET_KEY);

    private TurMinioStorageService service;

    @BeforeEach
    void setUp() throws Exception {
        TurMinioProperty minio = new TurMinioProperty();
        minio.setEndpoint(MINIO.getS3URL());
        minio.setAccessKey(ACCESS_KEY);
        minio.setSecretKey(SECRET_KEY);
        minio.setBucket(BUCKET);

        TurStorageProperty storage = new TurStorageProperty();
        storage.setMinio(minio);

        TurConfigProperties config = new TurConfigProperties();
        config.setStorage(storage);

        service = new TurMinioStorageService(config);
        // init() is package-private and normally invoked by Spring's PostConstruct;
        // we call it explicitly here so the bucket gets created before the test.
        Method init = TurMinioStorageService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(service);
    }

    @Test
    void uploadStream_thenStat_returnsObjectMetadata() {
        byte[] payload = "hello world".getBytes(StandardCharsets.UTF_8);

        service.uploadStream("docs/hello.txt",
                new ByteArrayInputStream(payload), payload.length, "text/plain");

        TurStorageObjectStat stat = service.statObject("docs/hello.txt");
        assertThat(stat.objectName()).isEqualTo("docs/hello.txt");
        assertThat(stat.size()).isEqualTo(payload.length);
        assertThat(stat.contentType()).isEqualTo("text/plain");
    }

    @Test
    void uploadStream_thenDownload_returnsSamePayload() throws Exception {
        byte[] payload = "binary-content".getBytes(StandardCharsets.UTF_8);
        service.uploadStream("docs/binary.bin",
                new ByteArrayInputStream(payload), payload.length, "application/octet-stream");

        try (InputStream is = service.downloadObject("docs/binary.bin")) {
            byte[] result = is.readAllBytes();
            assertThat(result).isEqualTo(payload);
        }
    }

    @Test
    void listObjects_withPrefix_returnsOnlyMatchingObjects() {
        upload("alpha/one.txt", "1");
        upload("alpha/two.txt", "2");
        upload("beta/three.txt", "3");

        List<TurAssetItem> alpha = service.listObjects("alpha/");
        List<String> alphaNames = alpha.stream().map(TurAssetItem::name).toList();

        assertThat(alphaNames)
                .contains("alpha/one.txt", "alpha/two.txt")
                .noneMatch(n -> n.startsWith("beta/"));
    }

    @Test
    void listAllObjects_returnsEverythingRecursive() {
        upload("root.txt", "x");
        upload("nested/deep/leaf.txt", "y");

        List<String> names = service.listAllObjects().stream().map(TurAssetItem::name).toList();

        assertThat(names).contains("root.txt", "nested/deep/leaf.txt");
    }

    @Test
    void deleteObject_removesObject() {
        upload("to-delete.txt", "bye");

        service.deleteObject("to-delete.txt");

        assertThatThrownBy(() -> service.statObject("to-delete.txt"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deleteObjectsWithPrefix_removesAllUnderPrefix() {
        upload("trash/a.txt", "a");
        upload("trash/b.txt", "b");
        upload("keep/c.txt", "c");

        service.deleteObjectsWithPrefix("trash/");

        List<String> remaining = service.listAllObjects().stream()
                .map(TurAssetItem::name).toList();
        assertThat(remaining)
                .noneMatch(n -> n.startsWith("trash/"))
                .contains("keep/c.txt");
    }

    @Test
    void createFolder_emitsDirectoryMarker() {
        service.createFolder("folder-only");

        // MinIO represents the folder as a zero-byte object with the trailing slash.
        TurStorageObjectStat stat = service.statObject("folder-only/");
        assertThat(stat.size()).isZero();
    }

    private void upload(String objectName, String content) {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        service.uploadStream(objectName, new ByteArrayInputStream(data), data.length, "text/plain");
    }
}
