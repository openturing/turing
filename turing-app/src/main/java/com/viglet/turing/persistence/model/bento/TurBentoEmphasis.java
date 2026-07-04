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
 * T574 / §XXXI.11 — how prominent one item is in a Bento list mosaic, decoupled
 * from its position. {@code LARGE} is the 2×2 hero tile, {@code MEDIUM} the 2×1
 * wide tile (today's non-featured default), {@code SMALL} the compact 1×1 tile a
 * masonry pass uses to fill holes.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurBentoEmphasis {
    SMALL,
    MEDIUM,
    LARGE
}
