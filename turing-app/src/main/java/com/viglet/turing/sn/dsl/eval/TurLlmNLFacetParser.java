/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.dsl.eval;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Production {@link TurNLFacetParser} (T385 / §XX.5): drives the configured
 * <b>default LLM</b> with a constrained prompt that turns prose into an
 * Elasticsearch-style query body, parsed into a {@link TurDslQueryRequest} with
 * the same {@code ObjectMapper} the {@code dsl_search} tool uses. This is the
 * hardened, reusable replacement for a catalog's hand-written {@code nlFilters}.
 *
 * <p>The prompt pins the two Block&nbsp;R invariants: the model may only use the
 * declared field names (never invent a field) and must omit any constraint that
 * does not map to a declared field — conservative grounding over guessing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurLlmNLFacetParser implements TurNLFacetParser {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;

    public TurLlmNLFacetParser(TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService secretCryptoService) {
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.llmModelFactory = llmModelFactory;
        this.secretCryptoService = secretCryptoService;
    }

    @Override
    public boolean isAvailable() {
        return resolveLlm() != null;
    }

    @Override
    public TurDslQueryRequest parse(ParseRequest request) {
        TurLLMInstance llm = resolveLlm();
        if (llm == null) {
            throw new TurNLFacetParseException(
                    "No usable default LLM configured for NL→facet parsing");
        }
        ChatModel chatModel = createChatModel(llm);
        String reply;
        try {
            List<Message> messages = List.of(
                    new SystemMessage(systemPrompt(request.schema())),
                    new UserMessage(userPrompt(request)));
            var result = chatModel.call(new Prompt(messages)).getResult();
            reply = result != null ? result.getOutput().getText() : null;
        } catch (RuntimeException e) {
            throw new TurNLFacetParseException("LLM call failed: " + e.getMessage(), e);
        }
        if (reply == null) {
            throw new TurNLFacetParseException("LLM returned an empty response");
        }
        return toQueryRequest(reply);
    }

    // ─────────────────────────── Prompt ───────────────────────────

    private String systemPrompt(List<TurNLFacetField> schema) {
        return """
                You translate a user's natural-language catalog query into an \
                Elasticsearch-compatible Query DSL JSON body. Output ONLY the JSON \
                object — no prose, no markdown fences.

                HARD RULES:
                - You may ONLY reference the fields listed in SCHEMA below. Never \
                  invent a field name.
                - If a constraint in the query does not map to a declared field, \
                  OMIT it. Do not guess. Returning fewer filters is correct; \
                  hallucinating a field is wrong.
                - Use a "bool" query with "filter" clauses for facets and ranges.
                - For an exact facet value use {"term":{"<field>":"<value>"}} (or \
                  "terms" for several values).
                - For numeric/currency comparisons use {"range":{"<field>":{"lte":N}}} \
                  ("under N" -> lte; "over N" -> gte; "between A and B" -> gte+lte).
                - For free-text intent that is not a facet, use \
                  {"match":{"_text_":"<words>"}}.
                - A superlative or comparative ("the lowest / highest / most / \
                  least <quality>", "the best by <quality>") expresses a SORT, not \
                  a filter. Emit "sort":[{"<numericField>":"asc|desc"}] on the \
                  numeric field whose DESCRIPTION matches that quality — ascending \
                  for a minimum, descending for a maximum. NEVER put the superlative \
                  word itself into a term/match filter: there is no facet value for \
                  it, so that returns zero results.
                - Facet/term values are matched case-insensitively, so casing need \
                  not be exact; still prefer the value as written in the query.

                SCHEMA (field: type [facet] - description):
                %s
                """.formatted(renderSchema(schema));
    }

    private String userPrompt(ParseRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("INDEX: ").append(request.index());
        if (StringUtils.hasText(request.locale())) {
            sb.append("\nLOCALE: ").append(request.locale());
        }
        sb.append("\nQUERY: ").append(request.query());
        sb.append("\n\nReturn the Query DSL JSON body.");
        return sb.toString();
    }

    private String renderSchema(List<TurNLFacetField> schema) {
        if (schema == null || schema.isEmpty()) {
            return "(no fields declared)";
        }
        List<String> lines = new ArrayList<>();
        for (TurNLFacetField f : schema) {
            if (f == null || f.name() == null) {
                continue;
            }
            String type = f.type() != null ? f.type().name().toLowerCase() : "text";
            // T802 — surface the declared field description so the model can map a
            // superlative ("cheapest") to the right numeric field and understand
            // what each facet means, not just its name.
            String description = StringUtils.hasText(f.description()) ? " - " + f.description().trim() : "";
            lines.add("- %s: %s%s%s".formatted(f.name(), type, f.facet() ? " [facet]" : "", description));
        }
        return String.join("\n", lines);
    }

    // ─────────────────────────── Parsing the reply ───────────────────────────

    private TurDslQueryRequest toQueryRequest(String reply) {
        if (!StringUtils.hasText(reply)) {
            throw new TurNLFacetParseException("LLM returned an empty reply");
        }
        String json = extractJson(reply);
        try {
            TurDslQueryRequest request = OBJECT_MAPPER.readValue(json, TurDslQueryRequest.class);
            if (request == null) {
                throw new TurNLFacetParseException("LLM reply parsed to a null query body");
            }
            return request;
        } catch (RuntimeException e) {
            throw new TurNLFacetParseException(
                    "Could not parse LLM reply as a Query DSL body: " + e.getMessage(), e);
        }
    }

    /** Strips markdown fences / prose around the JSON object. */
    static String extractJson(String reply) {
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return reply.substring(start, end + 1);
        }
        return reply.trim();
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
