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

package com.viglet.turing.exchange.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAgentChatMode;

import tools.jackson.databind.json.JsonMapper;

/**
 * T1004 — pins the chat mode to the export/import round trip.
 *
 * <p>The field was absent from {@link TurAIAgentExchange} while the seeded
 * bundles that ship the public demo were expected to carry it: a bundle asking
 * for {@code AUTO} produced a {@code CALL} agent, silently, because nothing on
 * either side of the exchange looked at the field. A dropped scalar leaves no
 * trace at import time, so the round trip is the thing worth asserting — not
 * either half on its own.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurAIAgentChatModeExchangeTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static TurAIAgent agentWith(TurAgentChatMode mode) {
        TurAIAgent agent = new TurAIAgent();
        agent.setId("agent-1");
        agent.setTitle("Seeded Demo Agent");
        agent.setChatMode(mode);
        return agent;
    }

    /** Mirrors what the import does with one exchange record. */
    private static TurAIAgent imported(TurAIAgentExchange exchange) {
        TurAIAgent target = new TurAIAgent();
        ReflectionTestUtils.invokeMethod(TurAIAgentImportService.class, "applyAgentScalars",
                target, exchange);
        return target;
    }

    @Test
    @DisplayName("the mode survives entity → exchange → JSON → exchange → entity")
    void roundTripsThroughJson() {
        TurAIAgentExchange exported = TurAIAgentExchange.fromEntity(agentWith(TurAgentChatMode.AUTO));
        assertEquals(TurAgentChatMode.AUTO, exported.getChatMode());

        String json = MAPPER.writeValueAsString(exported);
        assertTrue(json.contains("\"chatMode\":\"AUTO\""),
                "the bundle on disk must name the mode: " + json);

        TurAIAgentExchange reread = MAPPER.readValue(json, TurAIAgentExchange.class);
        assertEquals(TurAgentChatMode.AUTO, imported(reread).getChatMode());
    }

    @Test
    @DisplayName("a bundle that names AUTO produces an AUTO agent, not the CALL default")
    void bundleAskingForAutoIsHonoured() throws Exception {
        TurAIAgentExchange exchange = MAPPER.readValue(
                "{\"id\":\"a\",\"title\":\"Seeded\",\"chatMode\":\"AUTO\"}",
                TurAIAgentExchange.class);

        assertEquals(TurAgentChatMode.AUTO, imported(exchange).getChatMode());
    }

    @Test
    @DisplayName("a bundle written before the field existed still lands on CALL")
    void olderBundleKeepsTheEntityDefault() throws Exception {
        TurAIAgentExchange exchange = MAPPER.readValue(
                "{\"id\":\"a\",\"title\":\"Seeded\"}", TurAIAgentExchange.class);

        assertNull(exchange.getChatMode(), "an omitted field must stay absent, not guess");
        assertEquals(TurAgentChatMode.CALL, imported(exchange).getChatMode());
    }

    @Test
    @DisplayName("STREAM travels too — the field is carried, not special-cased for AUTO")
    void carriesEveryMode() {
        for (TurAgentChatMode mode : TurAgentChatMode.values()) {
            TurAIAgentExchange exported = TurAIAgentExchange.fromEntity(agentWith(mode));
            TurAIAgentExchange reread = MAPPER.readValue(
                    MAPPER.writeValueAsString(exported), TurAIAgentExchange.class);
            assertEquals(mode, imported(reread).getChatMode(), "mode " + mode + " did not survive");
        }
    }
}
