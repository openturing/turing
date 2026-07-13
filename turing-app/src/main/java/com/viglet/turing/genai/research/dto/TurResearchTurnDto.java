/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research.dto;

/**
 * One question/answer turn in a synthetic-user interview transcript
 * (Block AW / §XLVI.2). {@code question} is the interviewer's prompt; {@code answer}
 * is the persona-fused response produced by the T578 persona-chat executor. Also
 * the JSON element persisted in {@code TurResearchInterview.transcriptJson}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurResearchTurnDto(int index, String question, String answer) {
}
