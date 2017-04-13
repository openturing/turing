package com.viglet.turing.nlp;

public enum VigNLPTermCase {
	CS(1), CI(2), UCS(3);

	private int id;

	VigNLPTermCase(int id) {
		this.id = id;
	}

	public int id() {
		return id;
	}
}
