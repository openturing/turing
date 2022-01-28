package com.viglet.turing.wem.beans;

import java.util.List;

public class TurAttrDef {
	private String tagName;
	private TurMultiValue multiValue;

	public TurAttrDef (String tagName, TurMultiValue multiValue) {
		this.tagName = tagName;
		this.multiValue = multiValue;
	}
	public String getTagName() {
		return tagName;
	}

	public void setTagName(String tagName) {
		this.tagName = tagName;
	}

	public List<String> getMultiValue() {
		return multiValue;
	}

	public void setMultiValue(TurMultiValue multiValue) {
		this.multiValue = multiValue;
	}
	
	public String toString() { 
	    return String.format("tagName: %s, multiValue: %s", tagName, multiValue);
	} 
}
