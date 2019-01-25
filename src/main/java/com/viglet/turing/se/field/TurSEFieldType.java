package com.viglet.turing.se.field;

public enum TurSEFieldType {
	INT(1), LONG(2), STRING(3), ARRAY(4), DATE(5), BOOL(6);

	private int id;

	TurSEFieldType(int id) {
		this.id = id;
	}

	public int id() {
		return id;
	}
}
