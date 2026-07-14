/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.research;

/**
 * T728 / §XLVI.4 — the LLM-driven stages of a Synthetic User Research study that
 * can ride their own "Big Shuffle" model lane. Mirrors the T517
 * {@code TurModelStage} idea (a stage resolves to an instance, fail-open to the
 * default) but the binding is per-study, not global. Saturation scoring is
 * deterministic / LLM-free, so it is deliberately not a stage here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurResearchStage {
    /** Interviewer sub-loop + persona/agent answers — the bulk of the LLM calls. */
    INTERVIEW,
    /** The T722 insights synthesis (executive summary + themes + recommendations). */
    SYNTHESIS
}
