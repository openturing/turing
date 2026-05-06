/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.asset.TurAssetTrainingRecordDomain;
import com.viglet.turing.persistence.model.asset.TurAssetTrainingRecord;
import com.viglet.turing.persistence.repository.asset.TurAssetTrainingRecordRepository;

/**
 * Unit tests for {@link TurAssetTrainingRecordRepositoryAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class TurAssetTrainingRecordRepositoryAdapterTest {

    @Mock
    private TurAssetTrainingRecordRepository turAssetTrainingRecordRepository;

    private TurAssetTrainingRecordRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurAssetTrainingRecordRepositoryAdapter(turAssetTrainingRecordRepository,
                Mappers.getMapper(TurAssetTrainingRecordDomainMapper.class));
    }

    @Test
    void findByIdProjectsAllScalars() {
        Instant trainedAt = Instant.parse("2026-04-15T10:30:00Z");
        TurAssetTrainingRecord entity = new TurAssetTrainingRecord();
        entity.setObjectName("bucket/folder/report.pdf");
        entity.setObjectPath("folder/report.pdf");
        entity.setFileName("report.pdf");
        entity.setContentType("application/pdf");
        entity.setFileSize(2_345_678L);
        entity.setChunkCount(42);
        entity.setTrainedAt(trainedAt);
        entity.setEmbeddingModelId("openai-text-embedding-3-small");
        entity.setEmbeddingStoreId("lucene-default");
        when(turAssetTrainingRecordRepository.findById("bucket/folder/report.pdf"))
                .thenReturn(Optional.of(entity));

        TurAssetTrainingRecordDomain domain = adapter.findById("bucket/folder/report.pdf")
                .orElseThrow();

        assertThat(domain.objectName()).isEqualTo("bucket/folder/report.pdf");
        assertThat(domain.objectPath()).isEqualTo("folder/report.pdf");
        assertThat(domain.fileName()).isEqualTo("report.pdf");
        assertThat(domain.contentType()).isEqualTo("application/pdf");
        assertThat(domain.fileSize()).isEqualTo(2_345_678L);
        assertThat(domain.chunkCount()).isEqualTo(42);
        assertThat(domain.trainedAt()).isEqualTo(trainedAt);
        assertThat(domain.embeddingModelId()).isEqualTo("openai-text-embedding-3-small");
        assertThat(domain.embeddingStoreId()).isEqualTo("lucene-default");
    }

    @Test
    void findByIdEmpty() {
        when(turAssetTrainingRecordRepository.findById("missing"))
                .thenReturn(Optional.empty());
        assertThat(adapter.findById("missing")).isEmpty();
    }

    @Test
    void findByObjectNameInDelegates() {
        TurAssetTrainingRecord entity = new TurAssetTrainingRecord();
        entity.setObjectName("a.txt");
        entity.setFileName("a.txt");
        when(turAssetTrainingRecordRepository.findByObjectNameIn(Set.of("a.txt", "b.txt")))
                .thenReturn(List.of(entity));

        List<TurAssetTrainingRecordDomain> result = adapter
                .findByObjectNameIn(Set.of("a.txt", "b.txt"));

        assertThat(result).singleElement()
                .satisfies(d -> assertThat(d.objectName()).isEqualTo("a.txt"));
    }

    @Test
    void findByFileNameContainingIgnoreCaseDelegates() {
        TurAssetTrainingRecord entity = new TurAssetTrainingRecord();
        entity.setObjectName("docs/spec.md");
        entity.setFileName("Spec.md");
        when(turAssetTrainingRecordRepository.findByFileNameContainingIgnoreCase("spec"))
                .thenReturn(List.of(entity));

        List<TurAssetTrainingRecordDomain> result = adapter
                .findByFileNameContainingIgnoreCase("spec");

        assertThat(result).singleElement()
                .satisfies(d -> assertThat(d.fileName()).isEqualTo("Spec.md"));
    }

    @Test
    void findByContentTypeDelegates() {
        TurAssetTrainingRecord entity = new TurAssetTrainingRecord();
        entity.setObjectName("img/photo.png");
        entity.setContentType("image/png");
        when(turAssetTrainingRecordRepository.findByContentType("image/png"))
                .thenReturn(List.of(entity));

        List<TurAssetTrainingRecordDomain> result = adapter.findByContentType("image/png");

        assertThat(result).singleElement()
                .satisfies(d -> assertThat(d.contentType()).isEqualTo("image/png"));
    }

    @Test
    void countByContentTypeDelegates() {
        when(turAssetTrainingRecordRepository.countByContentType("application/pdf"))
                .thenReturn(7L);

        assertThat(adapter.countByContentType("application/pdf")).isEqualTo(7L);
    }
}
