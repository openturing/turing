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

package com.viglet.turing.se.facet;

import java.util.LinkedHashMap;

public class TurSEFacetResult {
	String facet;
	LinkedHashMap<String, TurSEFacetResultAttr> turSEFacetResultAttr = new LinkedHashMap<String, TurSEFacetResultAttr>();

	public String getFacet() {
		return facet;
	}

	public void setFacet(String facet) {
		this.facet = facet;
	}

	public LinkedHashMap<String, TurSEFacetResultAttr> getTurSEFacetResultAttr() {
		return turSEFacetResultAttr;
	}

	public void setTurSEFacetResultAttr(LinkedHashMap<String, TurSEFacetResultAttr> turSEFacetResultAttr) {
		this.turSEFacetResultAttr = turSEFacetResultAttr;
	}
	public void add(String attribute, TurSEFacetResultAttr turSEFacetResultAttr) {
		this.turSEFacetResultAttr.put(attribute, turSEFacetResultAttr);
	}
}
