/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.api.kb;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.kb.TurKnowledgeBaseService;
import com.viglet.turing.persistence.dto.kb.TurKnowledgeBaseDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T669 / §XL (Block AQ) — management REST API for the Knowledge Base aggregate
 * root. Admin console surface, secured by the {@code anyRequest().authenticated()}
 * rule in the security config (like the sibling console CRUD APIs).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/kb")
@Tag(name = "Knowledge Base", description = "Knowledge Base (microthesauri) API")
public class TurKnowledgeBaseAPI {

    private final TurKnowledgeBaseService turKnowledgeBaseService;

    public TurKnowledgeBaseAPI(TurKnowledgeBaseService turKnowledgeBaseService) {
        this.turKnowledgeBaseService = turKnowledgeBaseService;
    }

    @Operation(summary = "List knowledge bases")
    @GetMapping
    public List<TurKnowledgeBaseDto> list() {
        return turKnowledgeBaseService.list();
    }

    @Operation(summary = "Show a knowledge base")
    @GetMapping("/{id}")
    public TurKnowledgeBaseDto get(@PathVariable String id) {
        return turKnowledgeBaseService.get(id)
                .orElseThrow(() -> TurNotFoundException.of("Knowledge Base", id));
    }

    @Operation(summary = "Create a knowledge base")
    @PostMapping
    public TurKnowledgeBaseDto create(@RequestBody TurKnowledgeBaseDto dto) {
        return turKnowledgeBaseService.create(dto);
    }

    @Operation(summary = "Update a knowledge base")
    @PutMapping("/{id}")
    public TurKnowledgeBaseDto update(@PathVariable String id, @RequestBody TurKnowledgeBaseDto dto) {
        return turKnowledgeBaseService.update(id, dto)
                .orElseThrow(() -> TurNotFoundException.of("Knowledge Base", id));
    }

    @Operation(summary = "Delete a knowledge base")
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String id) {
        return turKnowledgeBaseService.delete(id);
    }
}
