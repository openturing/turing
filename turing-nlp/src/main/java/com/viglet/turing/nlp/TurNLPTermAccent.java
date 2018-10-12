package com.viglet.turing.nlp;

public enum TurNLPTermAccent {
	AS(1), AI(2);

	private int id;

	TurNLPTermAccent(int id) {
		this.id = id;
	}

	public int id() {
		return id;
	}
}
