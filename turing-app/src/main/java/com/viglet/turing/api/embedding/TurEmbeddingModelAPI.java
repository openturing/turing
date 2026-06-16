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
import org.springframework.web.bind.annotation.RestController;

import org.springframework.data.domain.Sort;

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

    public TurEmbeddingModelAPI(TurEmbeddingModelRepository turEmbeddingModelRepository,
            TurEmbeddingModelMapper turEmbeddingModelMapper,
            TurInfraTenantScope tenantScope) {
        this.turEmbeddingModelRepository = turEmbeddingModelRepository;
        this.turEmbeddingModelMapper = turEmbeddingModelMapper;
        this.tenantScope = tenantScope;
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
