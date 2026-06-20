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
package com.viglet.turing.lucene;

import org.apache.lucene.search.Query;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.persistence.model.sn.TurSNSite;

/**
 * The four inputs that define a single Lucene search execution: the open index
 * {@code instance}, the {@code turSNSite} whose schema governs field handling,
 * the compiled Lucene {@code query} and the request {@code params} (paging,
 * sort hints). Bundled into one record so {@link TurLuceneResultProcessor#getResults}
 * carries only the result-shaping arguments (facet/highlight fields, sort,
 * start time) as loose parameters and stays below the parameter threshold.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurLuceneSearch(
        TurLuceneInstance instance,
        TurSNSite turSNSite,
        Query query,
        TurSEParameters params) {
}
