package com.viglet.turing.se.field;

public class VigSEFieldMap {

	String field;
	VigSEFieldType type;
	String alias;
	VigSEFieldRequired required;
	
	public VigSEFieldMap(String field, VigSEFieldType type, String alias, VigSEFieldRequired required ) {
		this.setField(field);
		this.setType(type);
		this.setAlias(alias);
		this.setRequired(required);
	}

	public VigSEFieldRequired getRequired() {
		return required;
	}

	public void setRequired(VigSEFieldRequired required) {
		this.required = required;
	}

	public String getField() {
		return field;
	}

	public void setField(String field) {
		this.field = field;
	}

	public VigSEFieldType getType() {
		return type;
	}

	public void setType(VigSEFieldType type) {
		this.type = type;
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

}
