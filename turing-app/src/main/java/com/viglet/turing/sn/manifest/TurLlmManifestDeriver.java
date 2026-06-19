/*
 * Copyright (C) 2016-2026 the original author or authors.
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

package com.viglet.turing.sn.manifest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletFieldType;
import com.viglet.core.manifest.VigletManifestDeriveRequest;
import com.viglet.core.manifest.VigletManifestDeriver;
import com.viglet.core.manifest.VigletManifestFieldObservation;
import com.viglet.core.manifest.VigletManifestHeuristics;
import com.viglet.core.manifest.VigletManifestSampleAnalyzer;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Production {@link VigletManifestDeriver} (T387 / §XX.7): derives a draft
 * {@link VigletFieldManifest} from a sample of a source's documents.
 *
 * <p>The work is split so the expensive, non-deterministic part is small and the
 * useful part runs even without an LLM:</p>
 * <ol>
 *   <li>{@link VigletManifestSampleAnalyzer} computes pure per-field statistics
 *       (coverage, observed types, cardinality, multi-valued) from the sample.</li>
 *   <li>{@link VigletManifestHeuristics} turns those into a baseline draft schema —
 *       this is the whole answer when no LLM is configured.</li>
 *   <li>When the default LLM <em>is</em> configured, it is grounded on the
 *       observations and asked to <b>refine</b> the baseline: better type calls,
 *       facet judgement and human descriptions. The model may only refine fields
 *       actually observed in the sample — invented field names are dropped and
 *       any field the model omits keeps its heuristic spec, so the draft never
 *       loses an observed field or hallucinates one (conservative grounding).</li>
 * </ol>
 *
 * <p>The neutral manifest model lives in viglet-core (T393); this implementation
 * is the per-product seam that binds it to Turing's LLM stack. The result is
 * always a <em>draft for human review</em> — it is returned to the caller, never
 * provisioned automatically.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurLlmManifestDeriver implements VigletManifestDeriver {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    /** Draft manifests start at schema version 1; the reviewer bumps as they edit. */
    private static final String DRAFT_SCHEMA_VERSION = "1";

    /** How many raw documents to show the model as concrete examples. */
    private static final int MAX_RAW_SAMPLES = 5;

    private final VigletManifestSampleAnalyzer sampleAnalyzer = new VigletManifestSampleAnalyzer();
    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;

    public TurLlmManifestDeriver(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService secretCryptoService) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.llmModelFactory = llmModelFactory;
        this.secretCryptoService = secretCryptoService;
    }

    @Override
    public boolean isLlmAvailable() {
        return resolveLlm() != null;
    }

    @Override
    public VigletFieldManifest derive(VigletManifestDeriveRequest request) {
        if (request == null || request.documents() == null || request.documents().isEmpty()) {
            throw new IllegalArgumentException("At least one sample document is required to derive a manifest");
        }

        List<VigletManifestFieldObservation> observations = sampleAnalyzer.analyze(request.documents());
        if (observations.isEmpty()) {
            throw new IllegalArgumentException("Sample documents contain no fields to derive a schema from");
        }

        // Deterministic baseline — the full answer with no LLM, and the per-field
        // fallback when the model declines/garbles a field.
        List<VigletFieldSpec> fields = VigletManifestHeuristics.toFieldSpecs(observations);

        TurLLMInstance llm = resolveLlm();
        if (llm != null) {
            fields = refineWithLlm(llm, request, observations, fields);
        }

        return new VigletFieldManifest(request.name(), request.description(), request.seInstanceId(),
                DRAFT_SCHEMA_VERSION, request.locales(), fields, null);
    }

    // ─────────────────────────── LLM refinement ───────────────────────────

    private List<VigletFieldSpec> refineWithLlm(TurLLMInstance llm,
            VigletManifestDeriveRequest request, List<VigletManifestFieldObservation> observations,
            List<VigletFieldSpec> baseline) {
        try {
            ChatModel chatModel = createChatModel(llm);
            List<Message> messages = List.of(
                    new SystemMessage(SYSTEM_PROMPT),
                    new UserMessage(userPrompt(observations, request.documents())));
            String reply = chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
            return merge(baseline, parseReply(reply));
        } catch (RuntimeException e) {
            log.warn("[T387] LLM manifest refinement failed, using heuristic draft only: {}",
                    e.getMessage());
            return baseline;
        }
    }

    /**
     * Overlays the model's per-field judgement onto the heuristic baseline. Only
     * fields present in the baseline (i.e. actually observed) are touched; an
     * invented field name in the reply is ignored, and an observed field the
     * model omitted keeps its heuristic spec.
     */
    private List<VigletFieldSpec> merge(List<VigletFieldSpec> baseline,
            List<DerivedField> derived) {
        Map<String, DerivedField> byName = new LinkedHashMap<>();
        for (DerivedField d : derived) {
            if (d != null && StringUtils.hasText(d.name())) {
                byName.put(d.name(), d);
            }
        }
        List<VigletFieldSpec> merged = new ArrayList<>(baseline.size());
        for (VigletFieldSpec base : baseline) {
            DerivedField d = byName.get(base.name());
            merged.add(d == null ? base : apply(base, d));
        }
        return merged;
    }

    private VigletFieldSpec apply(VigletFieldSpec base, DerivedField d) {
        return VigletFieldSpec.builder()
                .name(base.name())
                .type(parseType(d.type(), base.type()))
                .mandatory(d.mandatory() != null ? d.mandatory() : base.mandatory())
                .multiValued(d.multiValued() != null ? d.multiValued() : base.multiValued())
                .facet(d.facet() != null ? d.facet() : base.facet())
                .description(StringUtils.hasText(d.description()) ? d.description() : base.description())
                .build();
    }

    private static VigletFieldType parseType(String type, VigletFieldType fallback) {
        if (!StringUtils.hasText(type)) {
            return fallback;
        }
        try {
            return VigletFieldType.valueOf(type.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    // ─────────────────────────── Prompt ───────────────────────────

    private static final String SYSTEM_PROMPT = """
                You design a search index schema (a "manifest") for a structured \
                catalog source. You are given OBSERVATIONS computed from a sample of \
                the source's documents and a few RAW example documents. Propose a \
                refined field schema as a JSON array — output ONLY the JSON array, no \
                prose, no markdown fences.

                Each array element is an object:
                  {"name": "<field>", "type": "<TYPE>", "facet": <bool>, \
                "multiValued": <bool>, "mandatory": <bool>, "description": "<short>"}

                TYPE must be one of: INT, LONG, STRING, TEXT, DATE, BOOL, FLOAT, \
                DOUBLE, CURRENCY.

                HARD RULES (conservative grounding):
                - Emit one element for EACH field in OBSERVATIONS, using its exact \
                  name. Never invent a field that is not in OBSERVATIONS.
                - Prefer the observed type; only widen it for a clear reason (e.g. \
                  mixed integers and decimals -> DOUBLE; a long free-text value -> TEXT).
                - A money/price field should be CURRENCY; an identifier or free-form \
                  prose should not be a facet; TEXT is never a facet.
                - facet = true for fields a user would filter or navigate by \
                  (category, modality, level, price range, date); false for free text \
                  and identifiers.
                - mandatory = true only when the field is present in (nearly) every \
                  document (high coverage).
                - Keep "description" short and factual; omit it if unsure.
                """;

    private String userPrompt(List<VigletManifestFieldObservation> observations,
            List<Map<String, Object>> documents) {
        StringBuilder sb = new StringBuilder();
        sb.append("OBSERVATIONS (field | coverage | observed types | distinct | multiValued | examples):\n");
        for (VigletManifestFieldObservation o : observations) {
            sb.append("- ").append(o.name())
                    .append(" | ").append(Math.round(o.coverage() * 100)).append("% (")
                    .append(o.presentCount()).append('/').append(o.totalDocuments()).append(')')
                    .append(" | ").append(renderTypes(o.observedTypes()))
                    .append(" | distinct=").append(o.distinctValueCount())
                    .append(" | multiValued=").append(o.multiValued())
                    .append(" | e.g. ").append(String.join(", ", o.sampleValues()))
                    .append('\n');
        }
        sb.append("\nRAW SAMPLE DOCUMENTS:\n");
        sb.append(renderRawSamples(documents));
        sb.append("\nReturn the JSON array of field definitions.");
        return sb.toString();
    }

    private static String renderTypes(java.util.Set<VigletFieldType> types) {
        if (types == null || types.isEmpty()) {
            return "STRING";
        }
        return types.stream().map(Enum::name).reduce((a, b) -> a + "/" + b).orElse("STRING");
    }

    private String renderRawSamples(List<Map<String, Object>> documents) {
        List<Map<String, Object>> sample = documents.stream()
                .filter(d -> d != null)
                .limit(MAX_RAW_SAMPLES)
                .toList();
        try {
            return OBJECT_MAPPER.writeValueAsString(sample);
        } catch (RuntimeException e) {
            return "(could not render sample documents)";
        }
    }

    // ─────────────────────────── Reply parsing ───────────────────────────

    private List<DerivedField> parseReply(String reply) {
        if (!StringUtils.hasText(reply)) {
            return List.of();
        }
        String json = extractJsonArray(reply);
        try {
            DerivedField[] parsed = OBJECT_MAPPER.readValue(json, DerivedField[].class);
            return parsed == null ? List.of() : List.of(parsed);
        } catch (RuntimeException e) {
            log.warn("[T387] Could not parse LLM manifest reply as a JSON array: {}", e.getMessage());
            return List.of();
        }
    }

    /** Strips prose/fences around the JSON array the model should return. */
    static String extractJsonArray(String reply) {
        int start = reply.indexOf('[');
        int end = reply.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return reply.substring(start, end + 1);
        }
        return reply.trim();
    }

    /** The model's per-field judgement; every field is optional and validated. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record DerivedField(
            String name,
            String type,
            Boolean facet,
            Boolean multiValued,
            Boolean mandatory,
            String description) {
    }

    // ─────────────────────────── LLM resolution ───────────────────────────

    private TurLLMInstance resolveLlm() {
        String llmId = globalSettingsService.getDefaultLlmId();
        if (!StringUtils.hasText(llmId)) {
            return null;
        }
        return llmInstanceRepository.findById(llmId)
                .filter(l -> l.getEnabled() == 1)
                .orElse(null);
    }

    private ChatModel createChatModel(TurLLMInstance llmInstance) {
        String apiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
        return llmModelFactory.createChatModel(llmInstance, apiKey);
    }
}
