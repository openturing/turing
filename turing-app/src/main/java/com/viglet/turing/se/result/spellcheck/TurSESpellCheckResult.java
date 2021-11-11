package com.viglet.turing.se.result.spellcheck;

public class TurSESpellCheckResult {

	private boolean isCorrected;
	private String correctedText;

	public TurSESpellCheckResult() {
		super();
		this.isCorrected = false;
		this.correctedText = "";
	}

	public TurSESpellCheckResult(boolean isCorrected, String correctedText) {
		super();
		this.isCorrected = isCorrected;
		this.correctedText = correctedText;
	}

	public boolean isCorrected() {
		return isCorrected;
	}

	public void setCorrected(boolean isCorrected) {
		this.isCorrected = isCorrected;
	}

	public String getCorrectedText() {
		return correctedText;
	}

	public void setCorrectedText(String correctedText) {
		this.correctedText = correctedText;
	}

}