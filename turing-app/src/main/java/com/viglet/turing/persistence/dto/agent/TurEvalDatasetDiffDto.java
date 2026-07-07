/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

import java.util.List;

/**
 * T598 / §XXXIII.13 — a row-level drift diff between two dataset versions.
 * Entries are row labels ({@code name} or id). Counts are the list sizes.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEvalDatasetDiffDto(
        String datasetId,
        int fromVersion,
        int toVersion,
        List<String> added,
        List<String> removed,
        List<String> changed) {
}
