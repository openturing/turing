package com.viglet.turing.persistence.model.embedding;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The persistent class for the embedding_model database table.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Getter
@Setter
@Entity
@Table(name = "embedding_model")
public class TurEmbeddingModel implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@TurAssignableUuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	/**
	 * T275 / §XIV.5.1 — owning tenant for BYO infra. A non-null value is a
	 * tenant's own instance (their key/endpoint); {@code null} is a
	 * platform-provided GLOBAL instance every tenant may use. Deliberately
	 * NOT {@code @TenantId} — that would filter the shared NULLs out; the
	 * repositories use an explicit {@code tenantId = :current OR IS NULL}.
	 */
	@jakarta.persistence.Column(name = "tenantId", length = 40)
	private String tenantId;

	@Column(nullable = false, length = 255)
	private String modelName;

	@Column(length = 500)
	private String description;

	@Column(length = 150)
	private String icon;

	@Column(nullable = false, length = 50)
	private String providerType;

	@ManyToOne
	@JoinColumn(name = "llm_instance_id")
	private TurLLMInstance turLLMInstance;

	@Column(length = 255)
	private String modelReference;

	@Column
	private Integer batchSize;

	@Column(length = 500)
	private String modelPath;

	@Column(length = 500)
	private String tokenizerPath;

	@Column(nullable = false)
	private int enabled;
}
