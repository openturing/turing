package com.viglet.turing.api.sn.bean.spellcheck;

import com.viglet.turing.api.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.se.result.spellcheck.TurSESpellCheckResult;

public class TurSNSiteSpellCheckBean {

	private boolean correctedText;
	private TurSNSiteSpellCheckText original;
	private TurSNSiteSpellCheckText corrected;

	public TurSNSiteSpellCheckBean(TurSNSiteSearchContext context, TurSESpellCheckResult turSESpellCheckResult) {
		super();
		this.correctedText = turSESpellCheckResult.isCorrected();
		this.original = new TurSNSiteSpellCheckText(context.getUri(), context.getTurSEParameters().getQuery(), true);
		this.corrected = new TurSNSiteSpellCheckText(context.getUri(), turSESpellCheckResult.getCorrectedText(), false);
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