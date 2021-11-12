/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.api.sn.bean;

import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.turing.api.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.se.similar.TurSESimilarResult;

@Component
public class TurSNSiteSearchWidgetBean {

	private List<TurSNSiteSearchFacetBean> facet;
	private TurSNSiteSearchFacetBean facetToRemove;
	private List<TurSESimilarResult> similar;
	private TurSNSiteSpellCheckBean spellCheck;

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

	public TurSNSiteSpellCheckBean getSpellCheck() {
		return spellCheck;
	}

	public void setSpellCheck(TurSNSiteSpellCheckBean spellCheck) {
		this.spellCheck = spellCheck;
	}

}
