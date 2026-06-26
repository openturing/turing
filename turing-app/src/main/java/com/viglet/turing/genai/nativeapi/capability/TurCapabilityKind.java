/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.nativeapi.capability;

/**
 * T432 / §X.18 — the three natures a capability can have. This is the single
 * tag that decides which UI surface a capability renders on, so the ~40 §X
 * capabilities don't end up in one undifferentiated list.
 *
 * <ul>
 *   <li>{@link #TOOL} — the model <em>invokes</em> it mid-turn (web_search,
 *       code_interpreter, the Turing native tool groups). Rendered in the
 *       agent's capability-first "Tools &amp; Capabilities" picker (T434).</li>
 *   <li>{@link #REQUEST_OPTION} — changes <em>how</em> the call is made and is
 *       never invocable (service tier, reasoning effort, citations, predicted
 *       / structured outputs, moderation, …). Rendered as settings toggles
 *       (T435), never in the tool picker.</li>
 *   <li>{@link #PLATFORM} — not per-agent at all (usage import, evals,
 *       distillation, nightly batch). Lives on its own admin screens.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurCapabilityKind {
    TOOL,
    REQUEST_OPTION,
    PLATFORM
}
