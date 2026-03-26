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

    public TurEmbeddingModelAPI(TurEmbeddingModelRepository turEmbeddingModelRepository,
            TurEmbeddingModelMapper turEmbeddingModelMapper) {
        this.turEmbeddingModelRepository = turEmbeddingModelRepository;
        this.turEmbeddingModelMapper = turEmbeddingModelMapper;
    }

    @Operation(summary = "Embedding Model List")
    @GetMapping
    @Secured({"ROLE_ADMIN", "EMBEDDING_VIEW"})
    public List<TurEmbeddingModelDto> turEmbeddingModelList() {
        return turEmbeddingModelMapper
                .toDtoList(this.turEmbeddingModelRepository.findAll(Sort.by(Sort.Order.asc("modelName").ignoreCase())));
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
                .toDto(this.turEmbeddingModelRepository.findById(id).orElse(new TurEmbeddingModel()));
    }

    @Operation(summary = "Update an Embedding Model")
    @PutMapping("/{id}")
    @Secured({"ROLE_ADMIN", "EMBEDDING_EDIT"})
    public TurEmbeddingModelDto turEmbeddingModelUpdate(@PathVariable String id,
            @RequestBody TurEmbeddingModelDto turEmbeddingModelDto) {
        return turEmbeddingModelRepository.findById(id).map(existing -> {
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
        this.turEmbeddingModelRepository.delete(id);
        return true;
    }

    @Operation(summary = "Create an Embedding Model")
    @PostMapping
    @Secured({"ROLE_ADMIN", "EMBEDDING_CREATE"})
    public TurEmbeddingModelDto turEmbeddingModelAdd(@RequestBody TurEmbeddingModelDto turEmbeddingModelDto) {
        TurEmbeddingModel turEmbeddingModel = turEmbeddingModelMapper.toEntity(turEmbeddingModelDto);
        this.turEmbeddingModelRepository.save(turEmbeddingModel);
        return turEmbeddingModelMapper.toDto(turEmbeddingModel);
    }
}
