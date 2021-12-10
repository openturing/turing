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

package com.viglet.turing.api.sn.bean;

import java.util.List;

import com.viglet.turing.se.similar.TurSESimilarResult;
/**
 * Details about facets and facet and "more like this" of Turing AI Semantic Navigation response.
 * 
 * @since 0.3.4
 */
public class TurSNSiteSearchWidgetBean {

	private List<TurSNSiteSearchFacetBean> facet;
	private TurSNSiteSearchFacetBean facetToRemove;
	private List<TurSESimilarResult> similar;
	
	public List<TurSNSiteSearchFacetBean> getFacet() {
		return facet;
	}
	public void setFacet(List<TurSNSiteSearchFacetBean> facet) {
		this.facet = facet;
	}
	public TurSNSiteSearchFacetBean getFacetToRemove() {
		return facetToRemove;
	}
	public void setFacetToRemove(TurSNSiteSearchFacetBean facetToRemove) {
		this.facetToRemove = facetToRemove;
	}
	public List<TurSESimilarResult> getSimilar() {
		return similar;
	}
	public void setSimilar(List<TurSESimilarResult> similar) {
		this.similar = similar;
	}

	

	

}
