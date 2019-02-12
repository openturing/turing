package com.viglet.turing.nlp;

public class TurNLPWord {
	String word;
	int position;
	
	public TurNLPWord(String word, int position) {
		this.setWord(word);
		this.setPosition(position);
	}
	public String getWord() {
		return word;
	}
	public void setWord(String word) {
		this.word = word;
	}
	public int getPosition() {
		return position;
	}
	public void setPosition(int position) {
		this.position = position;
	}
	
	
}
