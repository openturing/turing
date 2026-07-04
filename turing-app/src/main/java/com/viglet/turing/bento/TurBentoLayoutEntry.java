/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.bento;

import com.viglet.turing.persistence.model.bento.TurBentoEmphasis;

/**
 * T574 / §XXXI.11 — one item's placement in a Bento list: its stable id, its
 * {@link TurBentoEmphasis size}, and an explicit {@code displayOrder} decoupled
 * from array position.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurBentoLayoutEntry(String itemId, TurBentoEmphasis emphasis, int displayOrder) {
}
