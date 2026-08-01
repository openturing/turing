/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.answer;

/**
 * T833 / §LXII.2 — payload of the answer stream's {@code trace} SSE event: a
 * minimal, honest summary of what the answer engine did. Kept deliberately small
 * here (how many sources grounded the answer, how long the answer ran); T843
 * (§LXII.12) enriches this into the full "searched X · read N · reranked · answered"
 * research trace over the {@code tool_call} events the runtime already emits.
 *
 * @param sourceCount  number of distinct sources that grounded the answer
 * @param answerLength character length of the synthesized answer text
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurAnswerTraceEvent(int sourceCount, int answerLength) {
}
