/*
 *
 * Copyright (C) 2016-2025 the original author or authors. 
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.api.llm;

import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
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
import com.viglet.turing.domain.llm.LlmInstanceUpdatedEvent;
import com.viglet.turing.domain.llm.LlmProviderType;
import com.viglet.turing.persistence.dto.llm.TurLLMInstanceDto;
import com.viglet.turing.persistence.mapper.llm.TurLLMInstanceMapper;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;
import com.viglet.turing.system.security.TurSecretCryptoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.annotation.Secured;

@RestController
@RequestMapping("/api/llm")
@Tag(name = "Large Language Model", description = "Large Language Model API")
public class TurLLMInstanceAPI {
    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurLLMInstanceMapper turLLMInstanceMapper;
    private final TurSecretCryptoService turSecretCryptoService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TurLLMInstanceAPI(TurLLMInstanceRepository turLLMInstanceRepository,
            TurLLMInstanceMapper turLLMInstanceMapper,
            TurSecretCryptoService turSecretCryptoService,
            ApplicationEventPublisher eventPublisher) {
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.turLLMInstanceMapper = turLLMInstanceMapper;
        this.turSecretCryptoService = turSecretCryptoService;
        this.eventPublisher = eventPublisher;
    }

    @Secured({"ROLE_ADMIN", "LLM_VIEW"})
    @Operation(summary = "Large Language Model List")
    @GetMapping
    public List<TurLLMInstanceDto> turLLMInstanceList() {
        return turLLMInstanceMapper
                .toDtoList(this.turLLMInstanceRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
    }

    @Secured({"ROLE_ADMIN", "LLM_CREATE"})
    @Operation(summary = "Large Language Model structure")
    @GetMapping("/structure")
    public TurLLMInstanceDto turLLMInstanceStructure() {
        TurLLMInstance turLLMInstance = new TurLLMInstance();
        turLLMInstance.setTurLLMVendor(new TurLLMVendor());
        return turLLMInstanceMapper.toDto(turLLMInstance);

    }

    @Secured({"ROLE_ADMIN", "LLM_VIEW"})
    @Operation(summary = "Show a Large Language Model")
    @GetMapping("/{id}")
    public TurLLMInstanceDto turLLMInstanceGet(@PathVariable String id) {
        return turLLMInstanceMapper.toDto(this.turLLMInstanceRepository.findById(id).orElse(new TurLLMInstance()));
    }

    @Secured({"ROLE_ADMIN", "LLM_EDIT"})
    @Operation(summary = "Update a Large Language Model")
    @PutMapping("/{id}")
    public TurLLMInstanceDto turLLMInstanceUpdate(@PathVariable String id, @RequestBody Map<String, Object> payload) {
        TurLLMInstance turLLMInstance = objectMapper.convertValue(payload, TurLLMInstance.class);
        String apiKey = payload.get("apiKey") instanceof String apiKeyValue ? apiKeyValue : null;
        return turLLMInstanceRepository.findById(id).map(turLLMInstanceEdit -> {
            turLLMInstanceEdit.setTitle(turLLMInstance.getTitle());
            turLLMInstanceEdit.setDescription(turLLMInstance.getDescription());
            turLLMInstanceEdit.setIcon(turLLMInstance.getIcon());
            turLLMInstanceEdit.setTurLLMVendor(turLLMInstance.getTurLLMVendor());
            turLLMInstanceEdit.setUrl(turLLMInstance.getUrl());
            turLLMInstanceEdit.setEnabled(turLLMInstance.getEnabled());
            turLLMInstanceEdit.setModelName(turLLMInstance.getModelName());
            turLLMInstanceEdit.setTemperature(turLLMInstance.getTemperature());
            turLLMInstanceEdit.setTopK(turLLMInstance.getTopK());
            turLLMInstanceEdit.setTopP(turLLMInstance.getTopP());
            turLLMInstanceEdit.setRepeatPenalty(turLLMInstance.getRepeatPenalty());
            turLLMInstanceEdit.setSeed(turLLMInstance.getSeed());
            turLLMInstanceEdit.setNumPredict(turLLMInstance.getNumPredict());
            turLLMInstanceEdit.setStop(turLLMInstance.getStop());
            turLLMInstanceEdit.setResponseFormat(turLLMInstance.getResponseFormat());
            turLLMInstanceEdit.setSupportedCapabilities(turLLMInstance.getSupportedCapabilities());
            turLLMInstanceEdit.setTimeout(turLLMInstance.getTimeout());
            turLLMInstanceEdit.setMaxRetries(turLLMInstance.getMaxRetries());
            turLLMInstanceEdit.setProviderOptionsJson(turLLMInstance.getProviderOptionsJson());
            turLLMInstanceEdit.setToolsEnabled(turLLMInstance.isToolsEnabled());
            if (StringUtils.hasText(apiKey)) {
                turLLMInstanceEdit.setApiKeyEncrypted(turSecretCryptoService.encrypt(apiKey));
            }
            this.turLLMInstanceRepository.save(turLLMInstanceEdit);
            eventPublisher.publishEvent(LlmInstanceUpdatedEvent.updated(
                    turLLMInstanceEdit.getId(),
                    LlmProviderType.of(resolveProviderPlugin(turLLMInstanceEdit))));
            return turLLMInstanceMapper.toDto(turLLMInstanceEdit);
        }).orElse(new TurLLMInstanceDto());

    }

    @Secured({"ROLE_ADMIN", "LLM_DELETE"})
    @Transactional
    @Operation(summary = "Delete a Large Language Model")
    @DeleteMapping("/{id}")
    public boolean turLLMInstanceDelete(@PathVariable String id) {
        this.turLLMInstanceRepository.delete(id);
        return true;
    }

    @Secured({"ROLE_ADMIN", "LLM_CREATE"})
    @Operation(summary = "Create a Large Language Model")
    @PostMapping
    public TurLLMInstanceDto turLLMInstanceAdd(@RequestBody Map<String, Object> payload) {
        TurLLMInstance turLLMInstance = objectMapper.convertValue(payload, TurLLMInstance.class);
        String apiKey = payload.get("apiKey") instanceof String apiKeyValue ? apiKeyValue : null;
        if (StringUtils.hasText(apiKey)) {
            turLLMInstance.setApiKeyEncrypted(turSecretCryptoService.encrypt(apiKey));
        }
        this.turLLMInstanceRepository.save(turLLMInstance);
        eventPublisher.publishEvent(LlmInstanceUpdatedEvent.created(
                turLLMInstance.getId(),
                LlmProviderType.of(resolveProviderPlugin(turLLMInstance))));
        return turLLMInstanceMapper.toDto(turLLMInstance);

    }

    /**
     * Mirrors the lookup used by {@code TurGenAiLlmProviderFactory}: the plugin
     * id falls back to the vendor id when the explicit plugin field is blank.
     * Defaults to {@code "unknown"} when the vendor isn't set yet so the value
     * object never receives a null.
     */
    private static String resolveProviderPlugin(TurLLMInstance instance) {
        TurLLMVendor vendor = instance.getTurLLMVendor();
        if (vendor == null) {
            return "unknown";
        }
        if (StringUtils.hasText(vendor.getPlugin())) {
            return vendor.getPlugin();
        }
        return StringUtils.hasText(vendor.getId()) ? vendor.getId() : "unknown";
    }
}
