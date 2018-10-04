package com.viglet.turing.bean.ml.sentence;

import org.springframework.stereotype.Component;

@Component
public class TurMLSentenceBean {
	String sentence;
	
	int turMLCategoryId;
	
	public String getSentence() {
		return sentence;
	}

	public void setSentence(String sentence) {
		this.sentence = sentence;
	}

	public int getTurMLCategoryId() {
		return turMLCategoryId;
	}

	public void setTurMLCategoryId(int turMLCategoryId) {
		this.turMLCategoryId = turMLCategoryId;
	}



}
