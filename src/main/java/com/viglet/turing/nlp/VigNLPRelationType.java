package com.viglet.turing.nlp;

public enum VigNLPRelationType {
	BT(1), NT(2), RT(3), U(4), UF(5);

	private int id;

	VigNLPRelationType(int id) {
		this.id = id;
	}

	public int id() {
		return id;
	}
}
