/*
 * Copyright (C) 2016-2022 the original author or authors. 
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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * List of documents with results by group of query of Turing AI Semantic
 * Navigation response.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 */

@RequiredArgsConstructor
@Accessors(chain = true)
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteSearchGroupBean {
	private String name;
	private int count;
	private int page;
	private int pageCount;
	private int pageEnd;
	private int pageStart;
	private int limit;
	private TurSNSiteSearchResultsBean results;
	private List<TurSNSiteSearchPaginationBean> pagination;
}
