package com.viglet.turing.nlp;

import java.util.LinkedHashMap;

public class TurNLPSentence {
	LinkedHashMap<String, TurNLPWord> words = new LinkedHashMap<String, TurNLPWord>();

	public LinkedHashMap<String, TurNLPWord> getWords() {
		return words;
	}

	public void setWords(LinkedHashMap<String, TurNLPWord> words) {
		this.words = words;
	}
	public void addWord(TurNLPWord word) {
		this.words.put(word.getWord(), word);
	}
}
