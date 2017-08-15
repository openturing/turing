package com.viglet.turing.se.field;

public class TurSEFieldRequired {
	boolean required;
	Object defaultValue;
	public boolean isRequired() {
		return required;
	}
	public TurSEFieldRequired(boolean required, Object defaultValue) {
		this.setRequired(required);
		this.setDefaultValue(defaultValue);		
	}
	public void setRequired(boolean required) {
		this.required = required;
	}
	public Object getDefaultValue() {
		return defaultValue;
	}
	public void setDefaultValue(Object defaultValue) {
		this.defaultValue = defaultValue;
	}
	
}
