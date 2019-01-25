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
