package com.viglet.turing.se.facet;


public class TurSEFacetResultAttr {
	String attribute;
	int count;
	
	public TurSEFacetResultAttr(String attribute, int count) {
		this.setAttribute(attribute);
		this.setCount(count);
	}
	public String getAttribute() {
		return attribute;
	}
	public void setAttribute(String attribute) {
		this.attribute = attribute;
	}
	public int getCount() {
		return count;
	}
	public void setCount(int count) {
		this.count = count;
	}
	
}
