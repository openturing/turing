package com.viglet.turing.api.sn.bean.spellcheck;

import java.net.URI;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.se.result.spellcheck.TurSESpellCheckResult;

public class TurSNSiteSpellCheckBean {

	private boolean correctedText;
	private TurSNSiteSpellCheckText original;
	private TurSNSiteSpellCheckText corrected;

	public TurSNSiteSpellCheckBean(URI uri, TurSNSite turSNSite, String originalText, TurSESpellCheckResult turSESpellCheckResult) {
		super();
		this.correctedText = turSESpellCheckResult.isCorrected();
		this.original = new TurSNSiteSpellCheckText(uri, turSNSite.getName(), originalText, true);
		this.corrected = new TurSNSiteSpellCheckText(uri, turSNSite.getName(), turSESpellCheckResult.getCorrectedText(), false);
	}
	
	public TurSNSiteSpellCheckText getOriginal() {
		return original;
	}

	public void setOriginal(TurSNSiteSpellCheckText original) {
		this.original = original;
	}

	public TurSNSiteSpellCheckText getCorrected() {
		return corrected;
	}

	public void setCorrected(TurSNSiteSpellCheckText corrected) {
		this.corrected = corrected;
	}

	public boolean isCorrectedText() {
		return correctedText;
	}

	public void setCorrectedText(boolean correctedText) {
		this.correctedText = correctedText;
	}

}