package com.viglet.turing.se.result;

import org.json.JSONObject;

public class TurSEResultAttr {
	String attribute;
	JSONObject attrJSON;
	
	public TurSEResultAttr(String attribute, JSONObject attrJSON) {
		this.setAttribute(attribute);
		this.setAttrJSON(attrJSON);
	}
	public String getAttribute() {
		return attribute;
	}
	public void setAttribute(String attribute) {
		this.attribute = attribute;
	}
	public JSONObject getAttrJSON() {
		return attrJSON;
	}
	public void setAttrJSON(JSONObject attrJSON) {
		this.attrJSON = attrJSON;
	}
	
}
