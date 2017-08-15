package com.viglet.turing.se.field;

public class TurSEFieldMap {

	String field;
	TurSEFieldType type;
	String alias;
	TurSEFieldRequired required;
	
	public TurSEFieldMap(String field, TurSEFieldType type, String alias, TurSEFieldRequired required ) {
		this.setField(field);
		this.setType(type);
		this.setAlias(alias);
		this.setRequired(required);
	}

	public TurSEFieldRequired getRequired() {
		return required;
	}

	public void setRequired(TurSEFieldRequired required) {
		this.required = required;
	}

	public String getField() {
		return field;
	}

	public void setField(String field) {
		this.field = field;
	}

	public TurSEFieldType getType() {
		return type;
	}

	public void setType(TurSEFieldType type) {
		this.type = type;
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

}
