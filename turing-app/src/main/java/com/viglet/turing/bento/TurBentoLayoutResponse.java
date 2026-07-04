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

import java.util.List;

/**
 * T574 / §XXXI.11 — the resolved layout for a list surface: the ordered/sized
 * entries, {@link TurBentoLayoutSource where they came from}, and whether the
 * current user may edit the global template (so the UI can offer "set as default
 * for everyone"). {@code DEFAULT} source ships empty entries — the client applies
 * the built-in featured=idx0 default itself.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurBentoLayoutResponse(String listId, TurBentoLayoutSource source, boolean canEditGlobal,
        List<TurBentoLayoutEntry> entries) {
}
