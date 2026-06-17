/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.sn.coverage;

import java.util.List;

/**
 * T388 — per-field coverage / completeness report for a Semantic Navigation
 * site: the total indexed document count plus the completeness of each enabled
 * field. Turns the T381 "absent ≠ empty" contract into a data-quality signal
 * that pinpoints which fields a source under-fills.
 *
 * <p>{@code supported} is {@code false} when the site's search engine cannot
 * report field presence (e.g. an engine without an existence primitive); in
 * that case per-field numbers are "unknown" and the UI degrades gracefully.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurSNFieldCoverageReport(
        String siteId,
        String siteName,
        long totalDocuments,
        boolean supported,
        List<TurSNFieldCoverage> fields) {
}
