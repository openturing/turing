package com.viglet.turing.se.result;

import java.util.HashMap;
import java.util.Map;

public class TurSEResult {

	Map<String, TurSEResultAttr> turSEResultAttr = new HashMap<String, TurSEResultAttr>();

	public Map<String, TurSEResultAttr> getTurSEResultAttr() {
		return turSEResultAttr;
	}

	public void setTurSEResultAttr(Map<String, TurSEResultAttr> turSEResultAttr) {
		this.turSEResultAttr = turSEResultAttr;
	}

	public void add(String attribute, TurSEResultAttr turSEResultAttr) {
		this.turSEResultAttr.put(attribute, turSEResultAttr);
	}
}
