package com.viglet.turing.sn.spotlight;

import java.io.Serializable;

import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;

public class TurSNSpotlightTermCacheBean implements Serializable {
	private static final long serialVersionUID = 1L;
	private String term;
	private String spotlightId;
	private TurSNSiteSpotlight spotlight;

	public TurSNSpotlightTermCacheBean(String term, TurSNSiteSpotlight spotlight) {
		super();
		this.term = term;
		this.spotlight = spotlight;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public String getSpotlightId() {
		return spotlightId;
	}

	public void setSpotlightId(String spotlightId) {
		this.spotlightId = spotlightId;
	}

	public TurSNSiteSpotlight getSpotlight() {
		return spotlight;
	}

	public void setSpotlight(TurSNSiteSpotlight spotlight) {
		this.spotlight = spotlight;
	}

}
