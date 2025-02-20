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

import com.google.inject.Inject;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.utils.TurPersistenceUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/llm")
@Tag(name = "Large Language Model", description = "Large Language Model API")
public class TurLLMInstanceAPI {
    private final TurLLMInstanceRepository turLLMInstanceRepository;

    @Inject
    public TurLLMInstanceAPI(TurLLMInstanceRepository turLLMInstanceRepository) {
        this.turLLMInstanceRepository = turLLMInstanceRepository;
    }

    @Operation(summary = "Large Language Model List")
    @GetMapping
    public List<TurLLMInstance> turLLMInstanceList() {
        return this.turLLMInstanceRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase());
    }

    @Operation(summary = "Large Language Model structure")
    @GetMapping("/structure")
    public TurLLMInstance turLLMInstanceStructure() {
        TurLLMInstance turLLMInstance = new TurLLMInstance();
        turLLMInstance.setTurLLMVendor(new TurLLMVendor());
        return turLLMInstance;

    }

    @Operation(summary = "Show a Large Language Model")
    @GetMapping("/{id}")
    public TurLLMInstance turLLMInstanceGet(@PathVariable String id) {
        return this.turLLMInstanceRepository.findById(id).orElse(new TurLLMInstance());
    }

    @Operation(summary = "Update a Large Language Model")
    @PutMapping("/{id}")
    public TurLLMInstance turLLMInstanceUpdate(@PathVariable String id, @RequestBody TurLLMInstance turLLMInstance) {
        return turLLMInstanceRepository.findById(id).map(turLLMInstanceEdit -> {
            turLLMInstanceEdit.setTitle(turLLMInstance.getTitle());
            turLLMInstanceEdit.setDescription(turLLMInstance.getDescription());
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
            this.turLLMInstanceRepository.save(turLLMInstanceEdit);
            return turLLMInstanceEdit;
        }).orElse(new TurLLMInstance());

    }

    @Transactional
    @Operation(summary = "Delete a Large Language Model")
    @DeleteMapping("/{id}")
    public boolean turLLMInstanceDelete(@PathVariable String id) {
        this.turLLMInstanceRepository.delete(id);
        return true;
    }

    @Operation(summary = "Create a Large Language Model")
    @PostMapping
    public TurLLMInstance turLLMInstanceAdd(@RequestBody TurLLMInstance turLLMInstance) {
        this.turLLMInstanceRepository.save(turLLMInstance);
        return turLLMInstance;

    }
}
