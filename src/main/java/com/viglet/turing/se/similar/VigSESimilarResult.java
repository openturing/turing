package com.viglet.turing.se.similar;

import java.util.HashMap;
import java.util.Map;

public class VigSESimilarResult {

	Map<String, VigSESimilarResultAttr> vigSESimilarResultAttr = new HashMap<String, VigSESimilarResultAttr>();

	public Map<String, VigSESimilarResultAttr> getVigSESimilarResultAttr() {
		return vigSESimilarResultAttr;
	}

	public void setVigSEFacetResultAttr(Map<String, VigSESimilarResultAttr> vigSEFacetResultAttr) {
		this.vigSESimilarResultAttr = vigSEFacetResultAttr;
	}

	public void add(String attribute, VigSESimilarResultAttr vigSEFacetResultAttr) {
		this.vigSESimilarResultAttr.put(attribute, vigSEFacetResultAttr);
	}
}
