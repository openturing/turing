/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.api.store;

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.persistence.utils.TurPersistenceUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/store")
@Tag(name = "Embedding Store", description = "Embedding Store API")
public class TurStoreInstanceAPI {
    private final TurStoreInstanceRepository turStoreInstanceRepository;

    @Inject
    public TurStoreInstanceAPI(TurStoreInstanceRepository turStoreInstanceRepository) {
        this.turStoreInstanceRepository = turStoreInstanceRepository;
    }

    @Operation(summary = "Embedding Store List")
    @GetMapping
    public List<TurStoreInstance> turStoreInstanceList() {
        return this.turStoreInstanceRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase());
    }

    @Operation(summary = "Embedding Store structure")
    @GetMapping("/structure")
    public TurStoreInstance turEmbeddingStoreInstanceStructure() {
        TurStoreInstance turStoreInstance = new TurStoreInstance();
        turStoreInstance.setTurStoreVendor(new TurStoreVendor());
        return turStoreInstance;

    }

    @Operation(summary = "Show a Embedding Store")
    @GetMapping("/{id}")
    public TurStoreInstance turStoreInstanceGet(@PathVariable String id) {
        return this.turStoreInstanceRepository.findById(id).orElse(new TurStoreInstance());
    }

    @Operation(summary = "Update a Embedding Store")
    @PutMapping("/{id}")
    public TurStoreInstance turStoreInstanceUpdate(@PathVariable String id, @RequestBody TurStoreInstance turStoreInstance) {
        return turStoreInstanceRepository.findById(id).map(turStoreInstanceEdit -> {
            turStoreInstanceEdit.setTitle(turStoreInstance.getTitle());
            turStoreInstanceEdit.setDescription(turStoreInstance.getDescription());
            turStoreInstanceEdit.setTurStoreVendor(turStoreInstance.getTurStoreVendor());
            turStoreInstanceEdit.setUrl(turStoreInstance.getUrl());
            turStoreInstanceEdit.setEnabled(turStoreInstance.getEnabled());
            this.turStoreInstanceRepository.save(turStoreInstanceEdit);
            return turStoreInstanceEdit;
        }).orElse(new TurStoreInstance());

    }

    @Transactional
    @Operation(summary = "Delete a Embedding Store")
    @DeleteMapping("/{id}")
    public boolean turStoreInstanceDelete(@PathVariable String id) {
        this.turStoreInstanceRepository.delete(id);
        return true;
    }

    @Operation(summary = "Create a Embedding Store")
    @PostMapping
    public TurStoreInstance turStoreInstanceAdd(@RequestBody TurStoreInstance turStoreInstance) {
        this.turStoreInstanceRepository.save(turStoreInstance);
        return turStoreInstance;

    }
}
