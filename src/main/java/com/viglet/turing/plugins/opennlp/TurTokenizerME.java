package com.viglet.turing.plugins.opennlp;

import java.io.Serializable;

import opennlp.tools.tokenize.TokenizerME;

public class TurTokenizerME implements Serializable{
	
	private static final long serialVersionUID = 1L;

	transient private TokenizerME tokenizerME;
	public TurTokenizerME(TokenizerME tokenizerME) {
		this.tokenizerME = tokenizerME;
	}
	public TokenizerME getTokenizerME() {
		return tokenizerME;
	}
	public void setTokenizerME(TokenizerME tokenizerME) {
		this.tokenizerME = tokenizerME;
	}

}
