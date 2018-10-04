package com.viglet.turing.api.sn.bean;

import org.springframework.stereotype.Component;

@Component
public class TurSNSiteSearchFacetLabelBean {

	private String lang;
	private String text;
	public String getLang() {
		return lang;
	}
	public void setLang(String lang) {
		this.lang = lang;
	}
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}

}
