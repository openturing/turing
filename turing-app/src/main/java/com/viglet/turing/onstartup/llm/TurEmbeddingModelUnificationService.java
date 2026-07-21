package com.viglet.turing.onstartup.llm;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Unifies legacy {@link TurEmbeddingModel} rows onto {@link TurLLMInstance}
 * (T755, ADR 0004). Runs at startup, idempotently: each embedding model becomes
 * an {@code llm_instance} row <b>with the same id</b> (reference stability — any
 * consumer that stored an {@code embeddingModelId} keeps resolving), carrying its
 * embedding default on the T753 {@code embeddingModelName} column.
 *
 * <p><b>Non-destructive.</b> The {@code embedding_model} table is left intact; it
 * stays the authoritative read source until the consumers are repointed (T756).
 * Nothing here deletes or edits an existing row, and a model already migrated
 * (an {@code llm_instance} with its id exists) is skipped.
 *
 * <p>Per ADR decision 5:
 * <ul>
 *   <li><b>cloud</b> ({@code turLLMInstance != null}) — copy vendor / url / key
 *       from the parent instance; embedding default = {@code modelReference};</li>
 *   <li><b>local / HF</b> — the matching in-process vendor (id = the embedding
 *       {@code providerType}, seeded in T754) + the ONNX serving fields.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurEmbeddingModelUnificationService {

    private static final int MAX_TITLE = 100;

    private final TurEmbeddingModelRepository embeddingModelRepository;
    private final TurLLMInstanceRepository instanceRepository;
    private final TurLLMVendorRepository vendorRepository;

    public TurEmbeddingModelUnificationService(TurEmbeddingModelRepository embeddingModelRepository,
            TurLLMInstanceRepository instanceRepository, TurLLMVendorRepository vendorRepository) {
        this.embeddingModelRepository = embeddingModelRepository;
        this.instanceRepository = instanceRepository;
        this.vendorRepository = vendorRepository;
    }

    /**
     * Creates a unified {@link TurLLMInstance} for every not-yet-migrated
     * embedding model. Idempotent — safe to call on every boot.
     *
     * @return the number of embedding models migrated this run
     */
    @Transactional
    public int reconcile() {
        int migrated = 0;
        for (TurEmbeddingModel em : embeddingModelRepository.findAll()) {
            if (em.getId() == null || instanceRepository.existsById(em.getId())) {
                continue; // id-preserving idempotency: already unified
            }
            if (unify(em)) {
                migrated++;
            }
        }
        if (migrated > 0) {
            log.info("Unified {} embedding model(s) onto llm_instance (T755).", migrated);
        }
        return migrated;
    }

    private boolean unify(TurEmbeddingModel em) {
        TurLLMInstance parent = em.getTurLLMInstance();
        TurLLMVendor vendor = parent != null
                ? parent.getTurLLMVendor()
                : vendorRepository.findById(em.getProviderType()).orElse(null);
        if (vendor == null) {
            log.warn("Skipping embedding-model unification for '{}' ({}): no resolvable vendor (providerType={}).",
                    em.getModelName(), em.getId(), em.getProviderType());
            return false;
        }

        TurLLMInstance inst = new TurLLMInstance();
        inst.setId(em.getId()); // id-preserving
        inst.setTenantId(em.getTenantId());
        inst.setEnabled(em.getEnabled());
        inst.setTitle(title(em));
        inst.setDescription(em.getDescription());
        inst.setIcon(em.getIcon());
        inst.setTurLLMVendor(vendor);
        // Embedding-only instance: no chat default, no tools / file upload.
        inst.setToolsEnabled(false);
        inst.setFileUploadEnabled(false);
        inst.setEmbeddingModelName(
                StringUtils.hasText(em.getModelReference()) ? em.getModelReference() : em.getModelName());

        if (parent != null) {
            inst.setUrl(parent.getUrl());
            inst.setApiKeyEncrypted(parent.getApiKeyEncrypted());
        } else {
            inst.setUrl(""); // in-process ONNX — no endpoint (url is non-null)
            inst.setEmbeddingModelPath(em.getModelPath());
            inst.setEmbeddingTokenizerPath(em.getTokenizerPath());
            inst.setEmbeddingBatchSize(em.getBatchSize());
        }
        instanceRepository.save(inst);
        return true;
    }

    /** A non-null title within the column limit — the model name, or a fallback. */
    private static String title(TurEmbeddingModel em) {
        String t = StringUtils.hasText(em.getModelName()) ? em.getModelName() : "Embedding " + em.getId();
        return t.length() > MAX_TITLE ? t.substring(0, MAX_TITLE) : t;
    }
}
