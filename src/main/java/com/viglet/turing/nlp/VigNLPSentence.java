package com.viglet.turing.nlp;

import java.util.LinkedHashMap;

public class VigNLPSentence {
	LinkedHashMap<String, VigNLPWord> words = new LinkedHashMap<String, VigNLPWord>();

	public LinkedHashMap<String, VigNLPWord> getWords() {
		return words;
	}

	public void setWords(LinkedHashMap<String, VigNLPWord> words) {
		this.words = words;
	}
	public void addWord(VigNLPWord word) {
		this.words.put(word.getWord(), word);
	}
}
