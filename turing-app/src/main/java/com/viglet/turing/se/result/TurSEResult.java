/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.se.result;

import lombok.*;
import lombok.experimental.Tolerate;

import java.util.Map;

import com.viglet.turing.commons.sn.bean.TurSNDuplicateCluster;

@Builder
@Data
public class TurSEResult {
	private Map<String, Object> fields;

	/**
	 * T389 / §XX.9 — optional "why this result ranked here" breakdown
	 * (lexical/semantic/fused/final ranks, RRF score, reranker delta), populated
	 * only by the hybrid ranking pipeline. Kept separate from {@link #fields} so
	 * it is never confused with a search-engine field. {@code null} on the legacy
	 * (lexical-only) path.
	 */
	private Map<String, Object> rankingExplanation;

	/**
	 * T390 / §XX.10 — optional cluster of near-identical documents (the same
	 * real-world entity from many sources) attached inline on hybrid sites with
	 * MoreLikeThis enabled. {@code null} when the result has no duplicate.
	 */
	private TurSNDuplicateCluster duplicateCluster;

	@Tolerate
	TurSEResult() {}
}
