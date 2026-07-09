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

import org.springframework.data.domain.Sort;

import com.viglet.turing.genai.provider.llm.TurEmbeddingDimensionService;
import com.viglet.turing.genai.provider.llm.TurHuggingFaceModelDiscoveryService;
import com.viglet.turing.genai.provider.llm.TurLlmModelDiscoveryService;
import com.viglet.turing.persistence.dto.embedding.TurEmbeddingModelDto;
import com.viglet.turing.persistence.mapper.embedding.TurEmbeddingModelMapper;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.repository.embedding.TurEmbeddingModelRepository;
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
    private final TurEmbeddingModelMapper turEmbeddingModelMapper;
    private final TurInfraTenantScope tenantScope;
    private final TurHuggingFaceModelDiscoveryService huggingFaceModelDiscoveryService;
    private final TurLlmModelDiscoveryService llmModelDiscoveryService;
    private final TurEmbeddingDimensionService embeddingDimensionService;

    public TurEmbeddingModelAPI(TurEmbeddingModelRepository turEmbeddingModelRepository,
            TurEmbeddingModelMapper turEmbeddingModelMapper,
            TurInfraTenantScope tenantScope,
            TurHuggingFaceModelDiscoveryService huggingFaceModelDiscoveryService,
            TurLlmModelDiscoveryService llmModelDiscoveryService,
            TurEmbeddingDimensionService embeddingDimensionService) {
        this.turEmbeddingModelRepository = turEmbeddingModelRepository;
        this.turEmbeddingModelMapper = turEmbeddingModelMapper;
        this.tenantScope = tenantScope;
        this.huggingFaceModelDiscoveryService = huggingFaceModelDiscoveryService;
        this.llmModelDiscoveryService = llmModelDiscoveryService;
        this.embeddingDimensionService = embeddingDimensionService;
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
        java.util.Comparator<TurEmbeddingModel> byModelName = java.util.Comparator.comparing(
                TurEmbeddingModel::getModelName,
                java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        return turEmbeddingModelMapper.toDtoList(tenantScope.visibleList(
                () -> this.turEmbeddingModelRepository.findAll(Sort.by(Sort.Order.asc("modelName").ignoreCase())),
                tenantId -> this.turEmbeddingModelRepository.findVisibleToTenant(tenantId)
                        .stream().sorted(byModelName).toList()));
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
        return turEmbeddingModelMapper
                .toDto(this.turEmbeddingModelRepository.findById(id).filter(tenantScope::isVisibleToTenant).orElse(new TurEmbeddingModel()));
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
}
