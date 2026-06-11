package com.viglet.turing.persistence.model.asset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Tracks which MinIO assets have been indexed into the embedding store.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "asset_training_record")
public class TurAssetTrainingRecord implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @Column(length = 768)
    private String objectName;

    /** T261 / §XIV.2.5 — multi-tenancy discriminator (see TurSNSite pilot). Hibernate-managed. */
    @org.hibernate.annotations.TenantId
    @jakarta.persistence.Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(length = 512)
    private String objectPath;

    @Column(length = 255)
    private String fileName;

    @Column(length = 100)
    private String contentType;

    private long fileSize;

    private int chunkCount;

    @Column(nullable = false)
    private Instant trainedAt;

    @Column(length = 255)
    private String embeddingModelId;

    @Column(length = 255)
    private String embeddingStoreId;
}
