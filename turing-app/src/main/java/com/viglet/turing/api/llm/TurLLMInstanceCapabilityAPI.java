/*
 * Copyright (C) 2016-2026 the original author or authors.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.nativeapi.TurNativeCapability;
import com.viglet.turing.genai.nativeapi.TurNativeCapabilityService;
import com.viglet.turing.persistence.model.llm.TurLLMInstanceCapability;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T132 / §X.2 — admin surface for the per-LLM-instance native capability
 * matrix (the {@code TurNativeProviderClient} seam opt-in).
 *
 * <p>{@code GET} returns the full catalogue (every {@link TurNativeCapability})
 * merged with the instance's persisted rows, so the UI can render a checklist
 * with the right defaults; {@code PUT}/{@code DELETE} toggle a single
 * capability.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@RestController
@RequestMapping("/api/llm/{instanceId}/capability")
@Tag(name = "LLM Native Capability",
        description = "Per-instance native (non-Spring-AI) capability matrix")
public class TurLLMInstanceCapabilityAPI {

    private final TurNativeCapabilityService capabilityService;
    private final TurLLMInstanceRepository instanceRepository;

    public TurLLMInstanceCapabilityAPI(TurNativeCapabilityService capabilityService,
            TurLLMInstanceRepository instanceRepository) {
        this.capabilityService = capabilityService;
        this.instanceRepository = instanceRepository;
    }

    /** Wire shape for a capability row in the matrix. */
    public record CapabilityDto(String key, String pluginType, boolean enabled, String configJson) {
    }

    /** Request body for {@link #upsert}. */
    public record CapabilityUpsertRequest(boolean enabled, String configJson) {
    }

    @Secured({"ROLE_ADMIN", "LLM_VIEW"})
    @Operation(summary = "List the native capability matrix for an LLM instance")
    @GetMapping
    public List<CapabilityDto> list(@PathVariable String instanceId) {
        Map<String, TurLLMInstanceCapability> stored = new java.util.HashMap<>();
        for (TurLLMInstanceCapability row : capabilityService.list(instanceId)) {
            stored.put(row.getCapabilityKey().toLowerCase(java.util.Locale.ROOT), row);
        }
        List<CapabilityDto> result = new ArrayList<>();
        for (TurNativeCapability capability : TurNativeCapability.values()) {
            TurLLMInstanceCapability row = stored.get(capability.getKey());
            result.add(new CapabilityDto(capability.getKey(), capability.getPluginType(),
                    row != null && row.isEnabled(), row == null ? null : row.getConfigJson()));
        }
        return result;
    }

    @Secured({"ROLE_ADMIN", "LLM_EDIT"})
    @Operation(summary = "Enable/disable + configure one native capability on an LLM instance")
    @PutMapping("/{key}")
    public ResponseEntity<CapabilityDto> upsert(@PathVariable String instanceId,
            @PathVariable String key, @RequestBody CapabilityUpsertRequest request) {
        if (instanceRepository.findById(instanceId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TurNativeCapability capability = TurNativeCapability.fromKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown native capability: " + key));
        TurLLMInstanceCapability saved = capabilityService.upsert(instanceId, capability,
                request.enabled(), request.configJson());
        return ResponseEntity.ok(new CapabilityDto(capability.getKey(), capability.getPluginType(),
                saved.isEnabled(), saved.getConfigJson()));
    }

    @Secured({"ROLE_ADMIN", "LLM_EDIT"})
    @Operation(summary = "Remove one native capability row from an LLM instance")
    @DeleteMapping("/{key}")
    public ResponseEntity<Void> delete(@PathVariable String instanceId, @PathVariable String key) {
        TurNativeCapability capability = TurNativeCapability.fromKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown native capability: " + key));
        capabilityService.delete(instanceId, capability);
        return ResponseEntity.noContent().build();
    }
}
