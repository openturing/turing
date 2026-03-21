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

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.genai.provider.store.TurGenAiStoreProviderFactory;
import com.viglet.turing.genai.provider.store.TurStoreCollectionInfo;
import com.viglet.turing.persistence.dto.store.TurStoreInstanceDto;
import com.viglet.turing.persistence.mapper.store.TurStoreInstanceMapper;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.store.TurStoreInstanceRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;
import com.viglet.turing.system.security.TurSecretCryptoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/store")
@Tag(name = "Embedding Store", description = "Embedding Store API")
public class TurStoreInstanceAPI {
    private final TurStoreInstanceRepository turStoreInstanceRepository;
    private final TurStoreInstanceMapper turStoreInstanceMapper;
    private final TurSecretCryptoService turSecretCryptoService;
    private final TurGenAiStoreProviderFactory storeProviderFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TurStoreInstanceAPI(TurStoreInstanceRepository turStoreInstanceRepository,
            TurStoreInstanceMapper turStoreInstanceMapper,
            TurSecretCryptoService turSecretCryptoService,
            TurGenAiStoreProviderFactory storeProviderFactory) {
        this.turStoreInstanceRepository = turStoreInstanceRepository;
        this.turStoreInstanceMapper = turStoreInstanceMapper;
        this.turSecretCryptoService = turSecretCryptoService;
        this.storeProviderFactory = storeProviderFactory;
    }

    @Operation(summary = "Embedding Store List")
    @GetMapping
    @Secured({"ROLE_ADMIN", "STORE_VIEW"})
    public List<TurStoreInstanceDto> turStoreInstanceList() {
        return turStoreInstanceMapper
                .toDtoList(this.turStoreInstanceRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
    }

    @Operation(summary = "Embedding Store structure")
    @GetMapping("/structure")
    @Secured({"ROLE_ADMIN", "STORE_VIEW"})
    public TurStoreInstanceDto turEmbeddingStoreInstanceStructure() {
        TurStoreInstance turStoreInstance = new TurStoreInstance();
        turStoreInstance.setTurStoreVendor(new TurStoreVendor());
        return turStoreInstanceMapper.toDto(turStoreInstance);

    }

    @Operation(summary = "Show a Embedding Store")
    @GetMapping("/{id}")
    @Secured({"ROLE_ADMIN", "STORE_VIEW"})
    public TurStoreInstanceDto turStoreInstanceGet(@PathVariable String id) {
        return turStoreInstanceMapper
                .toDto(this.turStoreInstanceRepository.findById(id).orElse(new TurStoreInstance()));
    }

    @Operation(summary = "Update a Embedding Store")
    @PutMapping("/{id}")
    @Secured({"ROLE_ADMIN", "STORE_EDIT"})
    public TurStoreInstanceDto turStoreInstanceUpdate(@PathVariable String id,
            @RequestBody Map<String, Object> payload) {
        TurStoreInstance turStoreInstance = objectMapper.convertValue(payload, TurStoreInstance.class);
        String credential = payload.get("credential") instanceof String credentialValue ? credentialValue : null;
        return turStoreInstanceRepository.findById(id).map(turStoreInstanceEdit -> {
            turStoreInstanceEdit.setTitle(turStoreInstance.getTitle());
            turStoreInstanceEdit.setDescription(turStoreInstance.getDescription());
            turStoreInstanceEdit.setIcon(turStoreInstance.getIcon());
            turStoreInstanceEdit.setTurStoreVendor(turStoreInstance.getTurStoreVendor());
            turStoreInstanceEdit.setUrl(turStoreInstance.getUrl());
            turStoreInstanceEdit.setEnabled(turStoreInstance.getEnabled());
            turStoreInstanceEdit.setCollectionName(turStoreInstance.getCollectionName());
            turStoreInstanceEdit.setProviderOptionsJson(turStoreInstance.getProviderOptionsJson());
            if (StringUtils.hasText(credential)) {
                turStoreInstanceEdit
                        .setCredentialEncrypted(turSecretCryptoService.encrypt(credential));
            }
            this.turStoreInstanceRepository.save(turStoreInstanceEdit);
            return turStoreInstanceMapper.toDto(turStoreInstanceEdit);
        }).orElse(new TurStoreInstanceDto());

    }

    @Transactional
    @Operation(summary = "Delete a Embedding Store")
    @DeleteMapping("/{id}")
    @Secured({"ROLE_ADMIN", "STORE_DELETE"})
    public boolean turStoreInstanceDelete(@PathVariable String id) {
        this.turStoreInstanceRepository.delete(id);
        return true;
    }

    @Operation(summary = "List collections in the Embedding Store")
    @GetMapping("/{id}/collections")
    @Secured({"ROLE_ADMIN", "STORE_VIEW"})
    public List<TurStoreCollectionInfo> turStoreInstanceCollections(@PathVariable String id) {
        return turStoreInstanceRepository.findById(id).map(instance -> {
            var provider = storeProviderFactory.getProvider(instance);
            String credential = turSecretCryptoService.decrypt(instance.getCredentialEncrypted());
            return provider.listCollections(instance, credential);
        }).orElse(List.of());
    }

    @Operation(summary = "Create a collection")
    @PostMapping("/{id}/collections/{collectionName}")
    @Secured({"ROLE_ADMIN", "STORE_CREATE"})
    public ResponseEntity<Void> createCollection(@PathVariable String id, @PathVariable String collectionName) {
        turStoreInstanceRepository.findById(id).ifPresent(instance -> {
            var provider = storeProviderFactory.getProvider(instance);
            String credential = turSecretCryptoService.decrypt(instance.getCredentialEncrypted());
            provider.createCollection(instance, credential, collectionName);
        });
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Clear all embeddings in a collection")
    @DeleteMapping("/{id}/collections/{collectionName}/clear")
    @Secured({"ROLE_ADMIN", "STORE_DELETE"})
    public ResponseEntity<Void> clearCollection(@PathVariable String id, @PathVariable String collectionName) {
        turStoreInstanceRepository.findById(id).ifPresent(instance -> {
            var provider = storeProviderFactory.getProvider(instance);
            String credential = turSecretCryptoService.decrypt(instance.getCredentialEncrypted());
            provider.clearCollection(instance, credential, collectionName);
        });
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Delete a collection")
    @DeleteMapping("/{id}/collections/{collectionName}")
    @Secured({"ROLE_ADMIN", "STORE_DELETE"})
    public ResponseEntity<Void> deleteCollection(@PathVariable String id, @PathVariable String collectionName) {
        turStoreInstanceRepository.findById(id).ifPresent(instance -> {
            var provider = storeProviderFactory.getProvider(instance);
            String credential = turSecretCryptoService.decrypt(instance.getCredentialEncrypted());
            provider.deleteCollection(instance, credential, collectionName);
        });
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get system information for an Embedding Store instance")
    @GetMapping("/{id}/system-info")
    @Secured({"ROLE_ADMIN", "STORE_VIEW"})
    public ResponseEntity<java.util.Map<String, String>> turStoreInstanceSystemInfo(@PathVariable String id) {
        return turStoreInstanceRepository.findById(id).map(instance -> {
            var provider = storeProviderFactory.getProvider(instance);
            String credential = turSecretCryptoService.decrypt(instance.getCredentialEncrypted());
            java.util.Map<String, String> info = provider.getSystemInfo(instance, credential);
            return ResponseEntity.ok(info);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create a Embedding Store")
    @PostMapping
    @Secured({"ROLE_ADMIN", "STORE_CREATE"})
    public TurStoreInstanceDto turStoreInstanceAdd(@RequestBody Map<String, Object> payload) {
        TurStoreInstance turStoreInstance = objectMapper.convertValue(payload, TurStoreInstance.class);
        String credential = payload.get("credential") instanceof String credentialValue ? credentialValue : null;
        if (StringUtils.hasText(credential)) {
            turStoreInstance.setCredentialEncrypted(turSecretCryptoService.encrypt(credential));
        }
        this.turStoreInstanceRepository.save(turStoreInstance);
        return turStoreInstanceMapper.toDto(turStoreInstance);

    }
}
