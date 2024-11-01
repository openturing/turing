package com.viglet.turing.commons.sn.search;

import java.io.Serializable;
import java.net.URI;
import java.util.Locale;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurSNSiteSearchContext implements Serializable {
	private String siteName;
	private TurSEParameters turSEParameters;
	private Locale locale;
	private TurSNSitePostParamsBean turSNSitePostParamsBean;
	private URI uri;

	public TurSNSiteSearchContext(String siteName, TurSEParameters turSEParameters,
								  Locale locale, URI uri,TurSNSitePostParamsBean turSNSitePostParamsBean) {
		super();
		this.siteName = siteName;
		this.turSEParameters = turSEParameters;
		this.locale = locale;
		this.uri = uri;
		this.turSNSitePostParamsBean = turSNSitePostParamsBean;
	}

	public TurSNSiteSearchContext(String siteName, TurSEParameters turSEParameters, Locale locale, URI uri) {
		this(siteName, turSEParameters, locale, uri, new TurSNSitePostParamsBean());
	}

	@Override
	public String toString() {
		return "TurSNSiteSearchContext{" +
				"siteName='" + siteName + '\'' +
				", turSEParameters=" + turSEParameters +
				", locale=" + locale +
				", turSNSitePostParamsBean=" + turSNSitePostParamsBean +
				", uri=" + uri +
				'}';
	}
}
