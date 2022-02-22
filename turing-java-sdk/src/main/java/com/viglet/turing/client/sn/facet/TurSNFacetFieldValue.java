package com.viglet.turing.client.sn.facet;

import com.viglet.turing.client.sn.TurSNItemWithAPI;

public class TurSNFacetFieldValue extends TurSNItemWithAPI {
	
	private String label;
	
	private int count;

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}
}
