package com.viglet.turing.se.result;

import java.util.HashMap;
import java.util.Map;

public class VigSEResult {

	Map<String, VigSEResultAttr> vigSEResultAttr = new HashMap<String, VigSEResultAttr>();

	public Map<String, VigSEResultAttr> getVigSEResultAttr() {
		return vigSEResultAttr;
	}

	public void setVigSEResultAttr(Map<String, VigSEResultAttr> vigSEResultAttr) {
		this.vigSEResultAttr = vigSEResultAttr;
	}

	public void add(String attribute, VigSEResultAttr vigSEResultAttr) {
		this.vigSEResultAttr.put(attribute, vigSEResultAttr);
	}
}
