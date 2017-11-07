package com.viglet.turing.api.sn;

import java.io.Serializable;

public class TurSNJob implements Serializable{

	private static final long serialVersionUID = 1L;
	private String siteId;
	private String json;

	public String getSiteId() {
		return siteId;
	}

	public void setSiteId(String siteId) {
		this.siteId = siteId;
	}

	public String getJson() {
		return json;
	}

	public void setJson(String json) {
		this.json = json;
	}

}
