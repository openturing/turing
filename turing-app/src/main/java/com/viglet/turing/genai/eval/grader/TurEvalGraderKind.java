/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.grader;

/**
 * T586 / §XXXIII.1 — the three industry-standard kinds of eval grader.
 *
 * <ul>
 *   <li>{@link #MODEL} — LLM-as-judge (semantic, non-deterministic).</li>
 *   <li>{@link #CODE} — deterministic / programmatic (CI-safe, no LLM).</li>
 *   <li>{@link #HUMAN} — human-in-the-loop review (defers a verdict).</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurEvalGraderKind {
    MODEL,
    CODE,
    HUMAN
}
