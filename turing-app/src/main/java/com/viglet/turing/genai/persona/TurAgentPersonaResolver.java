/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurChatFlowState;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.repository.agent.TurChatFlowStateRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Resolves <em>which</em> persona an AI agent should speak with for a given
 * conversation. The agent has a catalog of allowed personas plus one default;
 * a chat flow can override the active persona mid-conversation by setting the
 * special variable {@link #ACTIVE_PERSONA_VAR} on the flow state. This
 * resolver is the single place that reconciles those two signals and hands
 * back exactly one persona.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>If the conversation has an active flow state with a non-blank
 *       {@code __activePersonaId} variable AND that persona is in the agent's
 *       catalog, return it.</li>
 *   <li>Otherwise, return {@code agent.getDefaultPersona()} (which may be
 *       {@code null} — the agent then speaks in the LLM's default voice).</li>
 * </ol>
 *
 * <p>An override pointing to a persona <em>not</em> in the agent's catalog is
 * treated as a misconfiguration: we log a warning and fall back to the
 * default. This protects the runtime from silently honouring a persona the
 * agent operator never intended to allow.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Service
public class TurAgentPersonaResolver {

    /**
     * Reserved variable name used by chat flows to override the active
     * persona. Lives inside the flow state's {@code variablesJson} alongside
     * any user-collected variables; the double-underscore prefix marks it as
     * engine-controlled rather than user-collected.
     */
    public static final String ACTIVE_PERSONA_VAR = "__activePersonaId";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TurChatFlowStateRepository flowStateRepository;

    public TurAgentPersonaResolver(TurChatFlowStateRepository flowStateRepository) {
        this.flowStateRepository = flowStateRepository;
    }

    /**
     * Returns the persona that should govern the next assistant turn. Safe to
     * call when {@code conversationId} is null or when there is no active
     * flow — falls through to the agent's default.
     */
    public TurPersona resolve(TurAIAgent agent, String conversationId) {
        if (agent == null) return null;
        TurPersona override = readOverride(agent, conversationId);
        if (override != null) return override;
        return agent.getDefaultPersona();
    }

    private TurPersona readOverride(TurAIAgent agent, String conversationId) {
        if (StringUtils.isBlank(conversationId) || agent.getId() == null) return null;
        // All flow states for this conversation across the agent's flows. The
        // most recently updated wins — when a flow node sets the override,
        // its state row is touched, so it'll be the freshest entry.
        Optional<TurChatFlowState> mostRecent = flowStateRepository
                .findByConversationIdAndFlow_TurAIAgent_Id(conversationId, agent.getId())
                .stream()
                .max((a, b) -> a.getUpdatedAt().compareTo(b.getUpdatedAt()));
        if (mostRecent.isEmpty()) return null;
        String overrideId = readActivePersonaId(mostRecent.get());
        if (StringUtils.isBlank(overrideId)) return null;

        if (agent.getPersonas() == null) return null;
        return agent.getPersonas().stream()
                .filter(p -> overrideId.equals(p.getId()))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("[PersonaResolver] flow override {} is not in agent {} catalog; "
                                    + "falling back to default", overrideId, agent.getId());
                    return null;
                });
    }

    private String readActivePersonaId(TurChatFlowState state) {
        String json = state.getVariablesJson();
        if (StringUtils.isBlank(json)) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = OBJECT_MAPPER.readValue(json, Map.class);
            Object value = map.get(ACTIVE_PERSONA_VAR);
            return value == null ? null : value.toString();
        } catch (JacksonException e) {
            log.debug("[PersonaResolver] could not parse variablesJson for state {}: {}",
                    state.getId(), e.getMessage());
            return null;
        }
    }
}
