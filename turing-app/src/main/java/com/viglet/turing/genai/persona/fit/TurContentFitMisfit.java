/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.fit;

/**
 * One thing in the content that does <em>not</em> fit the audience persona
 * (Block AA / §XXVI.4). The {@code span} is verbatim text from the source
 * (grounding hard-fail drops any span not present in it); {@code reason} is one
 * of {@code too-complex} | {@code jargon} | {@code tone} | {@code
 * missing-context}; {@code suggestion} is an optional rewrite.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurContentFitMisfit(String span, String reason, String suggestion) {
}
