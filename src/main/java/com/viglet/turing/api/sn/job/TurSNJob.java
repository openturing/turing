package com.viglet.turing.api.sn.job;

import java.io.Serializable;

import org.springframework.stereotype.Component;

@Component
public class TurSNJob implements Serializable{

	private static final long serialVersionUID = 1L;
	private String siteId;
	
	private TurSNJobItems turSNJobItems;
	
	public String getSiteId() {
		return siteId;
	}

	public void setSiteId(String siteId) {
		this.siteId = siteId;
	}

	public TurSNJobItems getTurSNJobItems() {
		return turSNJobItems;
	}

	public void setTurSNJobItems(TurSNJobItems turSNJobItems) {
		this.turSNJobItems = turSNJobItems;
	}



	
}
