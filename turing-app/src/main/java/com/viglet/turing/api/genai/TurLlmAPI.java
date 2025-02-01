/*
 * Copyright (C) 2016-2024 the original author or authors.
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
package com.viglet.turing.api.genai;

import com.google.inject.Inject;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.llm.TurLlm;
import dev.langchain4j.data.document.Metadata;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/llm")
@Tag(name = "LLM", description = "LLM")
public class TurLlmAPI {
    private final TurLlm turLlm;
    public static final String PROMPT = """
            Using only this RAG data. Answer in Portuguese.
            You are a helpful assistant that can answer questions about the Knowledge base.
            """;

    @Inject
    public TurLlmAPI(TurLlm turLlm) {
        super();
        this.turLlm = turLlm;
    }

    @GetMapping("chat")
    public TurChatMessage chat(@RequestParam(required = false, name = TurSNParamType.QUERY) String q) {
        return turLlm.assistant(PROMPT, q);
    }

    @GetMapping("chat-test")
    public TurChatMessage chatTest(@RequestParam(required = false, name = TurSNParamType.QUERY) String q) {
        turLlm.removeAllDocuments();
        addDocuments();
        return turLlm.assistant(PROMPT, q);
    }

    public void addDocuments() {
        turLlm.addDocument("The Horse is white. His name is Isaiah",
                new Metadata(Map.of("id", "123")));
        turLlm.addDocument("The priest's house is white. The priest's name is Augusto",
                new Metadata(Map.of("id", "124")));
    }
}
