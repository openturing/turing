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
 * Voice tone of a {@link TurPersona}. Mapped to the LLM as a directive in
 * the system prompt's "Style Guidelines" block.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
public enum TurPersonaTone {
    FORMAL,
    CASUAL,
    TECHNICAL,
    EXECUTIVE
}
