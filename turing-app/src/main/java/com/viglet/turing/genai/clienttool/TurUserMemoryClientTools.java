/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.clienttool;

import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.agent.TurAIAgent;

/**
 * T446 / §XXIII.5 — the built-in cross-conversation memory client tools.
 *
 * <p>The agent calls {@code remember_fact} to persist a durable fact about the
 * user and {@code recall_user_memory} to read back what it knows. They are
 * <em>client</em> tools (T438) because the host SDK is what holds the stable
 * {@code userId}: its handlers ({@code useTuringUserMemory}) call the
 * {@code /sn/{site}/user-memory} REST endpoints with that id, so the server never
 * has to thread userId through the chat path. Folded into the advertised tool set
 * when {@code TurAIAgent#isUserMemoryEnabled()}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurUserMemoryClientTools implements TurBuiltInClientToolProvider {

    public static final String REMEMBER_FACT = "remember_fact";
    public static final String RECALL_USER_MEMORY = "recall_user_memory";

    private static final String REMEMBER_FACT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "key": { "type": "string",
                         "description": "Short category, e.g. 'preferences', 'role', 'goal'." },
                "value": { "type": "string", "description": "The durable fact to remember." }
              },
              "required": ["key","value"]
            }""";

    private final List<TurClientTool> declarations = List.of(
            new TurClientTool(RECALL_USER_MEMORY,
                    "Recall durable facts you previously remembered about THIS user across past "
                            + "conversations. Call once early to personalise. Takes no arguments.",
                    TurClientTool.EMPTY_OBJECT_SCHEMA),
            new TurClientTool(REMEMBER_FACT,
                    "Persist ONE durable fact about the user (a stable preference, role, or goal — "
                            + "never transient chat content) so future conversations remember it. "
                            + "Re-using the same 'key' overwrites that fact.",
                    REMEMBER_FACT_SCHEMA));

    @Override
    public boolean appliesTo(TurAIAgent agent) {
        return agent != null && agent.isUserMemoryEnabled();
    }

    @Override
    public List<TurClientTool> declarations(TurAIAgent agent) {
        return declarations;
    }
}
