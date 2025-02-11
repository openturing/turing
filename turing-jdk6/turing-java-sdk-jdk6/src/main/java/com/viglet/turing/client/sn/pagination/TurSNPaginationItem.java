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

package com.viglet.turing.client.sn.pagination;


import com.viglet.turing.api.sn.bean.TurSNSiteSearchPaginationBean;
import com.viglet.turing.client.sn.TurSNItemWithAPI;

/**
 * Pagination of results of Turing AI Semantic Navigation response with friendly
 * attributes.
 * 
 * @since 0.3.4
 */
public class TurSNPaginationItem extends TurSNItemWithAPI {
	
	private TurSNPaginationType type;
	private String label;
	private int pageNumber;

	public TurSNPaginationItem() {
		super();
	}

	public TurSNPaginationItem(TurSNSiteSearchPaginationBean turSNSiteSearchPaginationBean) {
		super();
		if (turSNSiteSearchPaginationBean != null) {
			this.setType(turSNSiteSearchPaginationBean.getType());
			this.setApiURL(turSNSiteSearchPaginationBean.getHref());
			this.setLabel(turSNSiteSearchPaginationBean.getText());
			this.setPageNumber(turSNSiteSearchPaginationBean.getPage());
		}
	}

	public TurSNPaginationType getType() {
		return type;
	}

	public void setType(TurSNPaginationType type) {
		this.type = type;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public int getPageNumber() {
		return pageNumber;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
	}

}
