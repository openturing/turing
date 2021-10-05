package com.viglet.turing.plugins.nlp.opennlp;

import java.io.Serializable;

import opennlp.tools.namefind.NameFinderME;

public class TurNameFinderME implements Serializable{
	
	private static final long serialVersionUID = 1L;

	transient private NameFinderME nameFinderME;
	public TurNameFinderME(NameFinderME nameFinderME) {
		this.nameFinderME = nameFinderME;
	}
	public NameFinderME getNameFinderME() {
		return nameFinderME;
	}
	public void setNameFinderME(NameFinderME nameFinderME) {
		this.nameFinderME = nameFinderME;
	}

}
