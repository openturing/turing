/*
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.turing.persistence.model.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TurLLMInstance.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurLLMInstanceTest {

    @Mock
    private TurLLMVendor turLLMVendor;

    private TurLLMInstance turLLMInstance;

    @BeforeEach
    void setUp() {
        turLLMInstance = new TurLLMInstance();
    }

    @Test
    void testGettersAndSetters() {
        String id = "llm-id-123";
        String title = "Test LLM Instance";
        String description = "Test LLM Description";
        int enabled = 1;
        String url = "http://localhost:11434";
        String modelName = "llama2";
        Double temperature = 0.7;
        Integer topK = 40;
        Double topP = 0.9;
        Double repeatPenalty = 1.1;
        Integer seed = 42;
        Integer numPredict = 256;
        String stop = "</s>";
        String responseFormat = "json";
        String supportedCapabilities = "chat,completion";
        String timeout = "30s";
        Integer maxRetries = 3;
        
        turLLMInstance.setId(id);
        turLLMInstance.setTitle(title);
        turLLMInstance.setDescription(description);
        turLLMInstance.setEnabled(enabled);
        turLLMInstance.setUrl(url);
        turLLMInstance.setTurLLMVendor(turLLMVendor);
        turLLMInstance.setModelName(modelName);
        turLLMInstance.setTemperature(temperature);
        turLLMInstance.setTopK(topK);
        turLLMInstance.setTopP(topP);
        turLLMInstance.setRepeatPenalty(repeatPenalty);
        turLLMInstance.setSeed(seed);
        turLLMInstance.setNumPredict(numPredict);
        turLLMInstance.setStop(stop);
        turLLMInstance.setResponseFormat(responseFormat);
        turLLMInstance.setSupportedCapabilities(supportedCapabilities);
        turLLMInstance.setTimeout(timeout);
        turLLMInstance.setMaxRetries(maxRetries);
        
        assertThat(turLLMInstance.getId()).isEqualTo(id);
        assertThat(turLLMInstance.getTitle()).isEqualTo(title);
        assertThat(turLLMInstance.getDescription()).isEqualTo(description);
        assertThat(turLLMInstance.getEnabled()).isEqualTo(enabled);
        assertThat(turLLMInstance.getUrl()).isEqualTo(url);
        assertThat(turLLMInstance.getTurLLMVendor()).isEqualTo(turLLMVendor);
        assertThat(turLLMInstance.getModelName()).isEqualTo(modelName);
        assertThat(turLLMInstance.getTemperature()).isEqualTo(temperature);
        assertThat(turLLMInstance.getTopK()).isEqualTo(topK);
        assertThat(turLLMInstance.getTopP()).isEqualTo(topP);
        assertThat(turLLMInstance.getRepeatPenalty()).isEqualTo(repeatPenalty);
        assertThat(turLLMInstance.getSeed()).isEqualTo(seed);
        assertThat(turLLMInstance.getNumPredict()).isEqualTo(numPredict);
        assertThat(turLLMInstance.getStop()).isEqualTo(stop);
        assertThat(turLLMInstance.getResponseFormat()).isEqualTo(responseFormat);
        assertThat(turLLMInstance.getSupportedCapabilities()).isEqualTo(supportedCapabilities);
        assertThat(turLLMInstance.getTimeout()).isEqualTo(timeout);
        assertThat(turLLMInstance.getMaxRetries()).isEqualTo(maxRetries);
    }

    @Test
    void testDefaultValues() {
        assertThat(turLLMInstance.getId()).isNull();
        assertThat(turLLMInstance.getTitle()).isNull();
        assertThat(turLLMInstance.getDescription()).isNull();
        assertThat(turLLMInstance.getEnabled()).isZero();
        assertThat(turLLMInstance.getUrl()).isNull();
        assertThat(turLLMInstance.getTurLLMVendor()).isNull();
        assertThat(turLLMInstance.getModelName()).isNull();
        assertThat(turLLMInstance.getTemperature()).isNull();
        assertThat(turLLMInstance.getTopK()).isNull();
        assertThat(turLLMInstance.getTopP()).isNull();
        assertThat(turLLMInstance.getRepeatPenalty()).isNull();
        assertThat(turLLMInstance.getSeed()).isNull();
        assertThat(turLLMInstance.getNumPredict()).isNull();
        assertThat(turLLMInstance.getStop()).isNull();
        assertThat(turLLMInstance.getResponseFormat()).isNull();
        assertThat(turLLMInstance.getSupportedCapabilities()).isNull();
        assertThat(turLLMInstance.getTimeout()).isNull();
        assertThat(turLLMInstance.getMaxRetries()).isNull();
    }

    @Test
    void testSetTemperatureWithDifferentValues() {
        turLLMInstance.setTemperature(0.0);
        assertThat(turLLMInstance.getTemperature()).isEqualTo(0.0);
        
        turLLMInstance.setTemperature(0.5);
        assertThat(turLLMInstance.getTemperature()).isEqualTo(0.5);
        
        turLLMInstance.setTemperature(1.0);
        assertThat(turLLMInstance.getTemperature()).isEqualTo(1.0);
    }

    @Test
    void testSetTopPWithDifferentValues() {
        turLLMInstance.setTopP(0.1);
        assertThat(turLLMInstance.getTopP()).isEqualTo(0.1);
        
        turLLMInstance.setTopP(0.9);
        assertThat(turLLMInstance.getTopP()).isEqualTo(0.9);
    }

    @Test
    void testSetNullValues() {
        turLLMInstance.setId("id");
        turLLMInstance.setTitle("title");
        turLLMInstance.setDescription("description");
        turLLMInstance.setUrl("url");
        turLLMInstance.setTurLLMVendor(turLLMVendor);
        turLLMInstance.setModelName("model");
        turLLMInstance.setTemperature(0.7);
        turLLMInstance.setTopK(40);
        turLLMInstance.setTopP(0.9);
        turLLMInstance.setRepeatPenalty(1.1);
        turLLMInstance.setSeed(42);
        turLLMInstance.setNumPredict(256);
        turLLMInstance.setStop("stop");
        turLLMInstance.setResponseFormat("json");
        turLLMInstance.setSupportedCapabilities("chat");
        turLLMInstance.setTimeout("30s");
        turLLMInstance.setMaxRetries(3);
        
        turLLMInstance.setId(null);
        turLLMInstance.setTitle(null);
        turLLMInstance.setDescription(null);
        turLLMInstance.setUrl(null);
        turLLMInstance.setTurLLMVendor(null);
        turLLMInstance.setModelName(null);
        turLLMInstance.setTemperature(null);
        turLLMInstance.setTopK(null);
        turLLMInstance.setTopP(null);
        turLLMInstance.setRepeatPenalty(null);
        turLLMInstance.setSeed(null);
        turLLMInstance.setNumPredict(null);
        turLLMInstance.setStop(null);
        turLLMInstance.setResponseFormat(null);
        turLLMInstance.setSupportedCapabilities(null);
        turLLMInstance.setTimeout(null);
        turLLMInstance.setMaxRetries(null);
        
        assertThat(turLLMInstance.getId()).isNull();
        assertThat(turLLMInstance.getTitle()).isNull();
        assertThat(turLLMInstance.getDescription()).isNull();
        assertThat(turLLMInstance.getUrl()).isNull();
        assertThat(turLLMInstance.getTurLLMVendor()).isNull();
        assertThat(turLLMInstance.getModelName()).isNull();
        assertThat(turLLMInstance.getTemperature()).isNull();
        assertThat(turLLMInstance.getTopK()).isNull();
        assertThat(turLLMInstance.getTopP()).isNull();
        assertThat(turLLMInstance.getRepeatPenalty()).isNull();
        assertThat(turLLMInstance.getSeed()).isNull();
        assertThat(turLLMInstance.getNumPredict()).isNull();
        assertThat(turLLMInstance.getStop()).isNull();
        assertThat(turLLMInstance.getResponseFormat()).isNull();
        assertThat(turLLMInstance.getSupportedCapabilities()).isNull();
        assertThat(turLLMInstance.getTimeout()).isNull();
        assertThat(turLLMInstance.getMaxRetries()).isNull();
    }

    @Test
    void testSerialVersionUID() {
        assertThat(TurLLMInstance.class)
                .hasDeclaredFields("serialVersionUID");
    }
}
