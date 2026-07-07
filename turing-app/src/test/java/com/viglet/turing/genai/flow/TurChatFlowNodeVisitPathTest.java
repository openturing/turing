/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * Pins the T237 node-visit path helper: ordered append with consecutive-dup
 * collapse, the {@link TurChatFlowNodeVisitPath#MAX_PATH} cap, and
 * null/blank/corrupt tolerance.
 */
class TurChatFlowNodeVisitPathTest {

    @Test
    void appendsInOrderFromEmpty() {
        String p = TurChatFlowNodeVisitPath.append(null, "start");
        p = TurChatFlowNodeVisitPath.append(p, "ai-name");
        p = TurChatFlowNodeVisitPath.append(p, "end");
        assertThat(TurChatFlowNodeVisitPath.parse(p))
                .containsExactly("start", "ai-name", "end");
    }

    @Test
    void collapsesConsecutiveDuplicates() {
        // A re-asked aiQuestion (rejected answer) must not inflate the path.
        String p = TurChatFlowNodeVisitPath.append(null, "ai-name");
        p = TurChatFlowNodeVisitPath.append(p, "ai-name");
        p = TurChatFlowNodeVisitPath.append(p, "ai-name");
        assertThat(TurChatFlowNodeVisitPath.parse(p)).containsExactly("ai-name");
    }

    @Test
    void keepsNonConsecutiveRepeats() {
        // A loop back to an earlier node IS a distinct visit.
        String p = TurChatFlowNodeVisitPath.append(null, "a");
        p = TurChatFlowNodeVisitPath.append(p, "b");
        p = TurChatFlowNodeVisitPath.append(p, "a");
        assertThat(TurChatFlowNodeVisitPath.parse(p)).containsExactly("a", "b", "a");
    }

    @Test
    void ignoresBlankNodeId() {
        String p = TurChatFlowNodeVisitPath.append(null, "a");
        assertThat(TurChatFlowNodeVisitPath.parse(TurChatFlowNodeVisitPath.append(p, "")))
                .containsExactly("a");
        assertThat(TurChatFlowNodeVisitPath.parse(TurChatFlowNodeVisitPath.append(p, null)))
                .containsExactly("a");
    }

    @Test
    void capsAtMaxPath() {
        String p = "[]";
        for (int i = 0; i < TurChatFlowNodeVisitPath.MAX_PATH + 50; i++) {
            p = TurChatFlowNodeVisitPath.append(p, "n" + i);
        }
        assertThat(TurChatFlowNodeVisitPath.parse(p)).hasSize(TurChatFlowNodeVisitPath.MAX_PATH);
        // The first MAX_PATH distinct ids are retained; later ones dropped.
        assertThat(TurChatFlowNodeVisitPath.parse(p))
                .containsExactlyElementsOf(
                        IntStream.range(0, TurChatFlowNodeVisitPath.MAX_PATH)
                                .mapToObj(i -> "n" + i).toList());
    }

    @Test
    void parseToleratesNullBlankAndCorrupt() {
        assertThat(TurChatFlowNodeVisitPath.parse(null)).isEmpty();
        assertThat(TurChatFlowNodeVisitPath.parse("")).isEmpty();
        assertThat(TurChatFlowNodeVisitPath.parse("   ")).isEmpty();
        assertThat(TurChatFlowNodeVisitPath.parse("not json")).isEmpty();
        assertThat(TurChatFlowNodeVisitPath.parse("{\"a\":1}")).isEmpty();
    }

    @Test
    void serializeNullIsEmptyArray() {
        assertThat(TurChatFlowNodeVisitPath.serialize(null)).isEqualTo("[]");
        assertThat(TurChatFlowNodeVisitPath.serialize(List.of())).isEqualTo("[]");
    }
}
