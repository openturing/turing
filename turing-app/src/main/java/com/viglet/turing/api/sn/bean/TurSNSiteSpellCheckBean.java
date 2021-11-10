package com.viglet.turing.api.sn.bean;

public class TurSNSiteSpellCheckBean {

	private boolean corrected;
	private String correctedText;

	public TurSNSiteSpellCheckBean() {
		super();
		this.corrected = false;
		this.correctedText = "";
	}

	public TurSNSiteSpellCheckBean(boolean corrected, String correctedText) {
		super();
		this.corrected = corrected;
		this.correctedText = correctedText;
	}

	public boolean isCorrected() {
		return corrected;
	}

	public void setCorrected(boolean corrected) {
		this.corrected = corrected;
	}

	public String getCorrectedText() {
		return correctedText;
	}

	public void setCorrectedText(String correctedText) {
		this.correctedText = correctedText;
	}

}
