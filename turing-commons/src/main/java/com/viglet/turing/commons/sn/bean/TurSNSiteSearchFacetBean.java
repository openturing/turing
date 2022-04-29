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

package com.viglet.turing.commons.sn.bean;

import java.util.List;

import com.viglet.turing.commons.se.field.TurSEFieldType;

public class TurSNSiteSearchFacetBean {

	private List<TurSNSiteSearchFacetItemBean> facets;
	private TurSNSiteSearchFacetLabelBean label;
	private String name;
	private String description;
	private TurSEFieldType type;
	private boolean multiValued;

	public List<TurSNSiteSearchFacetItemBean> getFacets() {
		return facets;
	}

	public void setFacets(List<TurSNSiteSearchFacetItemBean> facets) {
		this.facets = facets;
	}

	public TurSNSiteSearchFacetLabelBean getLabel() {
		return label;
	}

	public void setLabel(TurSNSiteSearchFacetLabelBean label) {
		this.label = label;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public TurSEFieldType getType() {
		return type;
	}

	public void setType(TurSEFieldType type) {
		this.type = type;
	}

	public boolean isMultiValued() {
		return multiValued;
	}

	public void setMultiValued(boolean multiValued) {
		this.multiValued = multiValued;
	}
	public void setMultiValued(int multiValued) {
		this.multiValued = multiValued == 1;
	}

}