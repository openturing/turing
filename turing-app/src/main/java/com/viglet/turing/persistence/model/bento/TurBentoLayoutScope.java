/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.bento;

/**
 * T574 / §XXXI.11 — the layer a persisted Bento layout belongs to. {@code USER}
 * is a per-user protective override; {@code GLOBAL} is the admin-saved template
 * that non-customizers inherit (and keep following). The resolver cascade is
 * {@code USER → GLOBAL → the built-in featured=idx0 default}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurBentoLayoutScope {
    USER,
    GLOBAL
}
