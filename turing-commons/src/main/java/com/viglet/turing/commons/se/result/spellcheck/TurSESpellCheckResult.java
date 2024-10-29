package com.viglet.turing.commons.se.result.spellcheck;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TurSESpellCheckResult {

	private boolean corrected;
	private String correctedText;
	private boolean usingCorrected;

	public TurSESpellCheckResult() {
		super();
		this.corrected = false;
		this.correctedText = "";
	}

	public TurSESpellCheckResult(boolean isCorrected, String correctedText) {
		super();
		this.corrected = isCorrected;
		this.correctedText = correctedText;
	}

	@Override
	public String toString() {
		return "TurSESpellCheckResult{" +
				"corrected=" + corrected +
				", correctedText='" + correctedText + '\'' +
				", usingCorrected=" + usingCorrected +
				'}';
	}
}