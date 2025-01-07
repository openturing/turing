package com.viglet.turing.connector.aem.commons.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Locale;


@RequiredArgsConstructor
@Accessors(chain = true)
@Getter
@Setter
public class TurAemSNSiteConfig {
	private String name;
	private Locale locale;

	public TurAemSNSiteConfig(String name, Locale locale) {
		super();
		this.name = name;
		this.locale = locale;
	}
}