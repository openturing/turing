package com.viglet.turing.api.embedding;

import java.util.List;

import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.genai.provider.llm.TurEmbeddingDimensionService;
import com.viglet.turing.genai.provider.llm.TurHuggingFaceModelDiscoveryService;
import com.viglet.turing.genai.provider.llm.TurHuggingFaceRepoResolver;
import com.viglet.turing.genai.provider.llm.TurLlmModelDiscoveryService;
import com.viglet.turing.genai.verify.TurModelVerifyResult;
import com.viglet.turing.genai.verify.TurModelVerifyService;
import com.viglet.turing.persistence.dto.embedding.TurEmbeddingModelDto;
import com.viglet.turing.persistence.mapper.embedding.TurEmbeddingModelMapper;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.tenant.TurInfraTenantScope;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST API for Embedding Model management.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@RestController
@RequestMapping("/api/embedding-model")
@Tag(name = "Embedding Model", description = "Embedding Model API")
public class TurEmbeddingModelAPI {
    private final TurEmbeddingModelRepository turEmbeddingModelRepository;
    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurEmbeddingModelMapper turEmbeddingModelMapper;
    private final TurInfraTenantScope tenantScope;
    private final TurHuggingFaceModelDiscoveryService huggingFaceModelDiscoveryService;
    private final TurLlmModelDiscoveryService llmModelDiscoveryService;
    private final TurEmbeddingDimensionService embeddingDimensionService;
    private final TurHuggingFaceRepoResolver huggingFaceRepoResolver;
    private final TurModelVerifyService modelVerifyService;

    public TurEmbeddingModelAPI(TurEmbeddingModelRepository turEmbeddingModelRepository,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurEmbeddingModelMapper turEmbeddingModelMapper,
            TurInfraTenantScope tenantScope,
            TurHuggingFaceModelDiscoveryService huggingFaceModelDiscoveryService,
            TurLlmModelDiscoveryService llmModelDiscoveryService,
            TurEmbeddingDimensionService embeddingDimensionService,
            TurHuggingFaceRepoResolver huggingFaceRepoResolver,
            TurModelVerifyService modelVerifyService) {
        this.turEmbeddingModelRepository = turEmbeddingModelRepository;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.turEmbeddingModelMapper = turEmbeddingModelMapper;
        this.tenantScope = tenantScope;
        this.huggingFaceModelDiscoveryService = huggingFaceModelDiscoveryService;
        this.llmModelDiscoveryService = llmModelDiscoveryService;
        this.embeddingDimensionService = embeddingDimensionService;
        this.huggingFaceRepoResolver = huggingFaceRepoResolver;
        this.modelVerifyService = modelVerifyService;
    }

    /**
     * T628 — lists the ONNX artifact variants a HuggingFace repo ships (e.g.
     * {@code onnx/model.onnx}, {@code onnx/model_quantized.onnx}) so the picker
     * can offer a size/latency-vs-accuracy choice. The chosen variant is stored
     * appended to the repo id in {@code modelReference} (see
     * {@link TurHuggingFaceRepoResolver#VARIANT_SEPARATOR}).
     */
    @Operation(summary = "List a HuggingFace model's ONNX artifact variants")
    @GetMapping("/hf-variants")
    @Secured({"ROLE_ADMIN", "EMBEDDING_VIEW", "EMBEDDING_CREATE", "EMBEDDING_EDIT"})
    public List<String> turEmbeddingHuggingFaceVariants(@RequestParam String repoId) {
        return huggingFaceRepoResolver.listOnnxVariants(repoId);
    }

    /**
     * T627 — probes a HuggingFace model's embedding dimension and compares it
     * with the current default embedding model's, so the picker can warn that
     * changing the dimension means a reindex + a matching vector store.
     */
    @Operation(summary = "Check a HuggingFace model's embedding dimension vs. the default")
    @GetMapping("/hf-dimension-check")
    @Secured({"ROLE_ADMIN", "EMBEDDING_VIEW", "EMBEDDING_CREATE", "EMBEDDING_EDIT"})
    public TurEmbeddingDimensionService.DimensionCheck turEmbeddingHuggingFaceDimensionCheck(
            @RequestParam String repoId) {
        return embeddingDimensionService.checkHuggingFace(repoId);
    }

    /**
     * T625 — lists <b>embedding-capable</b> models for an LLM vendor so the
     * embedding form's model field can be a picker instead of free text (the
     * same combobox the chat LLM form got in T577, filtered to embeddings).
     * Serves a live, filtered list from the vendor API when a key is available
     * (typed-but-unsaved {@code apiKey}, or reused from {@code instanceId});
     * otherwise the bundled catalog filtered to embedding models. The picker
     * stays editable, so any unlisted id can still be typed.
     */
    @Operation(summary = "List embedding-capable models for an LLM vendor")
    @PostMapping("/llm-models")
    @Secured({"ROLE_ADMIN", "EMBEDDING_VIEW", "EMBEDDING_CREATE", "EMBEDDING_EDIT"})
    public TurLlmModelDiscoveryService.Result turEmbeddingLlmModels(
            @RequestBody TurEmbeddingLlmModelListRequest request) {
        return llmModelDiscoveryService.listEmbeddingModels(request.vendorId(), request.instanceId(),
                request.apiKey(), request.url(), request.providerOptionsJson());
    }

    /** Request body for {@link #turEmbeddingLlmModels(TurEmbeddingLlmModelListRequest)}. */
    public record TurEmbeddingLlmModelListRequest(String vendorId, String instanceId, String apiKey,
            String url, String providerOptionsJson) {
    }

    /**
     * T624 — lists ONNX-verified HuggingFace embedding models for the
     * {@code HUGGINGFACE} provider picker. Serves a live, ONNX-probed list from
     * {@code huggingface.co/api/models} when reachable; otherwise the bundled
     * curated catalog. The result records its source ({@code LIVE}/
     * {@code CATALOG}/{@code NONE}) so the UI can badge it.
     */
    @Operation(summary = "List selectable HuggingFace embedding models")
    @GetMapping("/hf-models")
    @Secured({"ROLE_ADMIN", "EMBEDDING_VIEW", "EMBEDDING_CREATE", "EMBEDDING_EDIT"})
    public TurHuggingFaceModelDiscoveryService.Result turEmbeddingHuggingFaceModels(
            @RequestParam(required = false) String query) {
        return huggingFaceModelDiscoveryService.listModels(query);
    }

    @Operation(summary = "Embedding Model List")
    @GetMapping
    @Secured({"ROLE_ADMIN", "EMBEDDING_VIEW"})
    public List<TurEmbeddingModelDto> turEmbeddingModelList() {
        // T757 / ADR 0004 — unified list: embedding-capable llm_instances (the
        // T755-migrated ones + instances given an embedding default via the LLM
        // form) mapped to the embedding-model shape, unioned with any legacy
        // embedding_model row not yet shadowed by an instance of the same id. This
        // is the single source every embedding picker (SN GenAI / store / agent /
        // default-embedding settings) reads, so they all see unified options.
        List<TurLLMInstance> instances = tenantScope.visibleList(
                turLLMInstanceRepository::findAll,
                turLLMInstanceRepository::findVisibleToTenant);
        List<TurEmbeddingModel> unified = new java.util.ArrayList<>();
        java.util.Set<String> instanceIds = new java.util.HashSet<>();
        for (TurLLMInstance inst : instances) {
            if (isEmbeddingCapable(inst)) {
                unified.add(instanceToEmbeddingModel(inst));
                instanceIds.add(inst.getId());
            }
        }
        List<TurEmbeddingModel> legacy = tenantScope.visibleList(
                turEmbeddingModelRepository::findAll,
                turEmbeddingModelRepository::findVisibleToTenant);
        for (TurEmbeddingModel em : legacy) {
            if (!instanceIds.contains(em.getId())) {
                unified.add(em);
            }
        }
        unified.sort(java.util.Comparator.comparing(TurEmbeddingModel::getModelName,
                java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return turEmbeddingModelMapper.toDtoList(unified);
    }

    /** An instance serves embeddings when it carries an embedding default or ONNX path. */
    private static boolean isEmbeddingCapable(TurLLMInstance inst) {
        return org.springframework.util.StringUtils.hasText(inst.getEmbeddingModelName())
                || org.springframework.util.StringUtils.hasText(inst.getEmbeddingModelPath());
    }

    /**
     * Maps an embedding-capable {@link TurLLMInstance} to the embedding-model shape
     * the pickers consume (id preserved). The connection is <b>not</b> attached
     * here — resolution re-derives it from the instance via
     * {@code TurRagContextBuilder.resolveEmbeddingEntity}.
     */
    private static TurEmbeddingModel instanceToEmbeddingModel(TurLLMInstance inst) {
        TurEmbeddingModel em = new TurEmbeddingModel();
        em.setId(inst.getId());
        em.setTenantId(inst.getTenantId());
        em.setModelName(org.springframework.util.StringUtils.hasText(inst.getEmbeddingModelName())
                ? inst.getEmbeddingModelName() : inst.getTitle());
        em.setModelReference(inst.getEmbeddingModelName());
        em.setProviderType(inst.getTurLLMVendor() != null ? inst.getTurLLMVendor().getId() : null);
        em.setEnabled(inst.getEnabled());
        em.setDescription(inst.getDescription());
        em.setIcon(inst.getIcon());
        em.setModelPath(inst.getEmbeddingModelPath());
        em.setTokenizerPath(inst.getEmbeddingTokenizerPath());
        em.setBatchSize(inst.getEmbeddingBatchSize());
        return em;
    }

    @Operation(summary = "Embedding Model structure")
    @GetMapping("/structure")
    @Secured({"ROLE_ADMIN", "EMBEDDING_VIEW"})
    public TurEmbeddingModelDto turEmbeddingModelStructure() {
        return turEmbeddingModelMapper.toDto(new TurEmbeddingModel());
    }

    @Operation(summary = "Show an Embedding Model")
    @GetMapping("/{id}")
    @Secured({"ROLE_ADMIN", "EMBEDDING_VIEW"})
    public TurEmbeddingModelDto turEmbeddingModelGet(@PathVariable String id) {
        // T758 — legacy embedding_model row first (so the standalone editor keeps
        // editing the real row), falling back to an embedding-capable llm_instance
        // mapped to the embedding shape, so an instance-backed embedding id is
        // fetchable by external callers too (endpoint aliased onto the unified entity).
        TurEmbeddingModel found = this.turEmbeddingModelRepository.findById(id)
                .filter(tenantScope::isVisibleToTenant)
                .orElseGet(() -> this.turLLMInstanceRepository.findById(id)
                        .filter(tenantScope::isVisibleToTenant)
                        .filter(TurEmbeddingModelAPI::isEmbeddingCapable)
                        .map(TurEmbeddingModelAPI::instanceToEmbeddingModel)
                        .orElse(new TurEmbeddingModel()));
        return turEmbeddingModelMapper.toDto(found);
    }

    @Operation(summary = "Update an Embedding Model")
    @PutMapping("/{id}")
    @Secured({"ROLE_ADMIN", "EMBEDDING_EDIT"})
    public TurEmbeddingModelDto turEmbeddingModelUpdate(@PathVariable String id,
            @RequestBody TurEmbeddingModelDto turEmbeddingModelDto) {
        return turEmbeddingModelRepository.findById(id).filter(tenantScope::isVisibleToTenant).map(existing -> {
            tenantScope.assertWritable(existing);
            turEmbeddingModelMapper.updateEntityFromDto(turEmbeddingModelDto, existing);
            turEmbeddingModelRepository.save(existing);
            return turEmbeddingModelMapper.toDto(existing);
        }).orElse(new TurEmbeddingModelDto());
    }

    @Transactional
    @Operation(summary = "Delete an Embedding Model")
    @DeleteMapping("/{id}")
    @Secured({"ROLE_ADMIN", "EMBEDDING_DELETE"})
    public boolean turEmbeddingModelDelete(@PathVariable String id) {
        // T365 — scope the by-id delete: a non-visible id (another tenant's
        // instance) is treated as not-found, never deleted.
        this.turEmbeddingModelRepository.findById(id).filter(tenantScope::isVisibleToTenant)
                .ifPresent(existing -> {
                    tenantScope.assertWritable(existing);
                    this.turEmbeddingModelRepository.delete(id);
                });
        return true;
    }

    @Operation(summary = "Create an Embedding Model")
    @PostMapping
    @Secured({"ROLE_ADMIN", "EMBEDDING_CREATE"})
    public TurEmbeddingModelDto turEmbeddingModelAdd(@RequestBody TurEmbeddingModelDto turEmbeddingModelDto) {
        TurEmbeddingModel turEmbeddingModel = turEmbeddingModelMapper.toEntity(turEmbeddingModelDto);
        tenantScope.stampOnCreate(turEmbeddingModel);
        this.turEmbeddingModelRepository.save(turEmbeddingModel);
        return turEmbeddingModelMapper.toDto(turEmbeddingModel);
    }

    /**
     * Verifies a saved embedding model by resolving it (local ONNX, HuggingFace
     * ONNX, or a cloud LLM instance) and embedding one probe string, so an
     * operator can confirm the model actually produces vectors. A bad config is
     * reported in the result body (HTTP 200 with {@code ok=false}); only an
     * unknown/foreign id is a 404.
     */
    @Operation(summary = "Verify that an Embedding Model is working")
    @PostMapping("/{id}/verify")
    @Secured({"ROLE_ADMIN", "EMBEDDING_VIEW", "EMBEDDING_CREATE", "EMBEDDING_EDIT"})
    public TurModelVerifyResult turEmbeddingModelVerify(@PathVariable String id) {
        return this.turEmbeddingModelRepository.findById(id).filter(tenantScope::isVisibleToTenant)
                .map(modelVerifyService::verifyEmbeddingModel)
                .orElseThrow(() -> TurNotFoundException.of("Embedding model", id));
    }
}
