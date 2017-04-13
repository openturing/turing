package com.viglet.turing.nlp;

public enum VigNLPTermAccent {
	AS(1), AI(2);

	private int id;

	VigNLPTermAccent(int id) {
		this.id = id;
	}

	public int id() {
		return id;
	}
}
