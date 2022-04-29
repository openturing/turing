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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;

/**
 * Pagination of results of Turing AI Semantic Navigation response.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteSearchPaginationBean {

	private TurSNPaginationType type;
	private String text;
	private String href;
	private int page;
	public TurSNPaginationType getType() {
		return type;
	}
	public void setType(TurSNPaginationType type) {
		this.type = type;
	}
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	public String getHref() {
		return href;
	}
	public void setHref(String href) {
		this.href = href;
	}
	public int getPage() {
		return page;
	}
	public void setPage(int page) {
		this.page = page;
	}
	
}
