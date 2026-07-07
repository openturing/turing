/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.dto.agent.TurEvalGraderStackDto;
import com.viglet.turing.persistence.dto.agent.TurEvalGraderStackDto.GraderConfigEntry;
import com.viglet.turing.persistence.model.agent.TurEvalGraderConfig;
import com.viglet.turing.persistence.model.agent.TurEvalGraderStack;
import com.viglet.turing.persistence.repository.agent.TurEvalGraderStackRepository;

/**
 * T600 / §XXXIII.15 — CRUD for named, reusable grader stacks. A stack is
 * created with its ordered grader entries in one call so it's usable before the
 * T599 Eval Studio ships an editor.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurEvalGraderStackService {

    private final TurEvalGraderStackRepository stackRepository;

    public TurEvalGraderStackService(TurEvalGraderStackRepository stackRepository) {
        this.stackRepository = stackRepository;
    }

    public List<TurEvalGraderStackDto> list() {
        return stackRepository.findByOrderByNameAsc().stream().map(TurEvalGraderStackService::toDto).toList();
    }

    public TurEvalGraderStackDto get(String stackId) {
        return stackRepository.findById(stackId).map(TurEvalGraderStackService::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Grader stack not found: " + stackId));
    }

    /** Creates a stack with its ordered grader entries (cascade-saved). */
    @Transactional
    public TurEvalGraderStackDto create(TurEvalGraderStackDto request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stack name is required");
        }
        TurEvalGraderStack stack = new TurEvalGraderStack();
        stack.setName(request.name());
        stack.setDescription(request.description());
        List<GraderConfigEntry> entries = request.configs() == null ? List.of() : request.configs();
        int order = 0;
        for (GraderConfigEntry entry : entries) {
            TurEvalGraderConfig config = new TurEvalGraderConfig();
            config.setGraderId(entry.graderId());
            config.setName(entry.name());
            config.setKind(entry.kind());
            config.setConfigJson(entry.configJson());
            config.setWeight(entry.weight() > 0 ? entry.weight() : 1d);
            config.setThreshold(entry.threshold());
            config.setBlocking(entry.blocking());
            config.setEnabled(entry.enabled());
            config.setSortOrder(entry.sortOrder() > 0 ? entry.sortOrder() : order);
            config.setTurEvalGraderStack(stack);
            stack.getConfigs().add(config);
            order++;
        }
        return toDto(stackRepository.save(stack));
    }

    @Transactional
    public void delete(String stackId) {
        stackRepository.deleteById(stackId);
    }

    private static TurEvalGraderStackDto toDto(TurEvalGraderStack stack) {
        List<GraderConfigEntry> configs = new ArrayList<>();
        stack.getConfigs().stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .forEach(c -> configs.add(new GraderConfigEntry(c.getGraderId(), c.getName(),
                        c.getKind(), c.getConfigJson(), c.getWeight(), c.getThreshold(),
                        c.getBlocking(), c.getEnabled(), c.getSortOrder())));
        return new TurEvalGraderStackDto(stack.getId(), stack.getName(), stack.getDescription(), configs);
    }
}
