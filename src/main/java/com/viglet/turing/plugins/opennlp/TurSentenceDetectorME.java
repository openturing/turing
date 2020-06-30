package com.viglet.turing.plugins.opennlp;

import java.io.Serializable;

import opennlp.tools.sentdetect.SentenceDetectorME;

public class TurSentenceDetectorME implements Serializable{
	
	private static final long serialVersionUID = 1L;

	transient private SentenceDetectorME sentenceDetectorME;
	public TurSentenceDetectorME(SentenceDetectorME sentenceDetectorME) {
		this.sentenceDetectorME = sentenceDetectorME;
	}
	public SentenceDetectorME getSentenceDetectorME() {
		return sentenceDetectorME;
	}
	public void setSentenceDetectorME(SentenceDetectorME sentenceDetectorME) {
		this.sentenceDetectorME = sentenceDetectorME;
	}

}