package com.viglet.turing.wem.config;

public class TurSNSiteConfig {

	private String name;
	private String locale;

	public TurSNSiteConfig(String name, String locale) {
		super();
		this.name = name;
		this.locale = locale;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

}
