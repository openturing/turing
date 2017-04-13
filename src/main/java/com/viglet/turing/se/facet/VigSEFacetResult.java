package com.viglet.turing.se.facet;

import java.util.LinkedHashMap;

public class VigSEFacetResult {
	String facet;
	LinkedHashMap<String, VigSEFacetResultAttr> vigSEFacetResultAttr = new LinkedHashMap<String, VigSEFacetResultAttr>();

	public String getFacet() {
		return facet;
	}

	public void setFacet(String facet) {
		this.facet = facet;
	}

	public LinkedHashMap<String, VigSEFacetResultAttr> getVigSEFacetResultAttr() {
		return vigSEFacetResultAttr;
	}

	public void setVigSEFacetResultAttr(LinkedHashMap<String, VigSEFacetResultAttr> vigSEFacetResultAttr) {
		this.vigSEFacetResultAttr = vigSEFacetResultAttr;
	}
	public void add(String attribute, VigSEFacetResultAttr vigSEFacetResultAttr) {
		this.vigSEFacetResultAttr.put(attribute, vigSEFacetResultAttr);
	}
}
