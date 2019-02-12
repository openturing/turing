package com.viglet.turing.nlp;

public enum TurNLPTermCase {
	CS(1), CI(2), UCS(3);

	private int id;

	TurNLPTermCase(int id) {
		this.id = id;
	}

	public int id() {
		return id;
	}
}
