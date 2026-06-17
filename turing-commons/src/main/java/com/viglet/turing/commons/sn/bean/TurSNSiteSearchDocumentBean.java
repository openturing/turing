/*
 * Copyright (C) 2016-2021 the original author or authors. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viglet.turing.commons.sn.bean;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Documents of results of Turing ES Semantic Navigation response.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class TurSNSiteSearchDocumentBean implements Serializable {

	private String source;
	private boolean elevate;
	private List<TurSNSiteSearchDocumentMetadataBean> metadata;
	@SuppressWarnings("java:S1948")
	private Map<String, Object> fields;
	/**
	 * T389 / §XX.9 — objective "why this result ranked here" breakdown attached
	 * by the hybrid ranking pipeline (ranks, RRF score, reranker delta). Omitted
	 * from the JSON when empty, so the legacy lexical path's response is byte-for-byte
	 * unchanged.
	 */
	@SuppressWarnings("java:S1948")
	@JsonInclude(JsonInclude.Include.NON_EMPTY)
	private Map<String, Object> rankingExplanation;

	/**
	 * T390 / §XX.10 — cluster of near-identical documents (the same real-world
	 * entity arriving from many sources), attached on hybrid sites with
	 * MoreLikeThis enabled so a catalog can collapse the duplicates while keeping
	 * each source's provenance. Omitted from the JSON when absent, so the legacy
	 * response is byte-for-byte unchanged.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private TurSNDuplicateCluster duplicateCluster;

}
