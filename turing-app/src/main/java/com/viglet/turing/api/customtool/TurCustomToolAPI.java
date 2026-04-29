/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.customtool;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.dto.customtool.TurCustomToolDto;
import com.viglet.turing.persistence.mapper.customtool.TurCustomToolMapper;
import com.viglet.turing.persistence.model.customtool.TurCustomTool;
import com.viglet.turing.persistence.repository.customtool.TurCustomToolRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@RestController
@RequestMapping("/api/custom-tool")
@Tag(name = "Custom Tool", description = "Custom Tool API")
public class TurCustomToolAPI {
    private final TurCustomToolRepository repository;
    private final TurCustomToolMapper mapper;

    public TurCustomToolAPI(TurCustomToolRepository repository, TurCustomToolMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Operation(summary = "Custom Tool List")
    @GetMapping
    public List<TurCustomToolDto> list() {
        return mapper.toDtoList(repository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
    }

    @Operation(summary = "Custom Tool structure")
    @GetMapping("/structure")
    public TurCustomToolDto structure() {
        return mapper.toDto(new TurCustomTool());
    }

    @Operation(summary = "Show a Custom Tool")
    @GetMapping("/{id}")
    public TurCustomToolDto get(@PathVariable String id) {
        return mapper.toDto(repository.findById(id).orElse(new TurCustomTool()));
    }

    @Operation(summary = "Update a Custom Tool")
    @PutMapping("/{id}")
    public TurCustomToolDto update(@PathVariable String id, @RequestBody TurCustomToolDto dto) {
        TurCustomTool source = mapper.toEntity(dto);
        return repository.findById(id).map(existing -> {
            mapper.updateEntity(source, existing);
            repository.save(existing);
            return mapper.toDto(existing);
        }).orElse(new TurCustomToolDto());
    }

    @Transactional
    @Operation(summary = "Delete a Custom Tool")
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String id) {
        repository.delete(id);
        return true;
    }

    @Operation(summary = "Create a Custom Tool")
    @PostMapping
    public TurCustomToolDto add(@RequestBody TurCustomToolDto dto) {
        TurCustomTool entity = mapper.toEntity(dto);
        repository.save(entity);
        return mapper.toDto(entity);
    }
}
