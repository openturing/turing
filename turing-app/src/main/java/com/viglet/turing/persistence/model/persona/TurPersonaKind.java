/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.persona;

/**
 * Discriminator that says what a {@link TurPersona} is usable <em>for</em>.
 *
 * <ul>
 *   <li>{@link #SPEAKER} — the legacy contract: a voice the assistant talks
 *       <em>as</em>. The prompt composer and agent attachment only act on
 *       SPEAKER/BOTH personas. This is the default, so every existing persona
 *       is byte-for-byte unchanged.</li>
 *   <li>{@link #AUDIENCE} — a reader proxy: the persona models <em>who is
 *       reading</em> (reading level, domain expertise, vocabulary ceiling…),
 *       consumed only by the content-fit evaluation path (Block AA). An
 *       AUDIENCE-only persona must never resolve as an agent's voice.</li>
 *   <li>{@link #BOTH} — a peer persona the assistant both speaks as <em>and</em>
 *       evaluates content for (e.g. "Lucas, fellow alumnus").</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurPersonaKind {
    SPEAKER,
    AUDIENCE,
    BOTH
}
