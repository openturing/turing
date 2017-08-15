package com.viglet.turing.se.similar;

import java.util.HashMap;
import java.util.Map;

public class TurSESimilarResult {

	Map<String, TurSESimilarResultAttr> turSESimilarResultAttr = new HashMap<String, TurSESimilarResultAttr>();

	public Map<String, TurSESimilarResultAttr> getTurSESimilarResultAttr() {
		return turSESimilarResultAttr;
	}

	public void setTurSEFacetResultAttr(Map<String, TurSESimilarResultAttr> turSEFacetResultAttr) {
		this.turSESimilarResultAttr = turSEFacetResultAttr;
	}

	public void add(String attribute, TurSESimilarResultAttr turSEFacetResultAttr) {
		this.turSESimilarResultAttr.put(attribute, turSEFacetResultAttr);
	}
}
