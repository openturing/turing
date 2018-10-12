package com.viglet.turing.sn;

public enum TurSNFieldType {
	SE(1), NER(2), THESAURUS(3);

	private int id;

	TurSNFieldType(int id) {
		this.id = id;
	}

	public int id() {
		return id;
	}
}
