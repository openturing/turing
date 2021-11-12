package com.viglet.turing.api.sn.bean.spellcheck;

import java.net.URI;

import com.viglet.turing.api.sn.search.TurSNParamType;
import com.viglet.turing.sn.TurSNUtils;

public class TurSNSiteSpellCheckText {
	private static final String TRUE = "1";
	private String text;
	private String link;

	public TurSNSiteSpellCheckText(URI uri, String text, boolean isOriginal) {
		super();
		URI uriModified = TurSNUtils.addOrReplaceParameter(uri, TurSNParamType.QUERY, text);
		if (isOriginal) {
			uriModified = TurSNUtils.addOrReplaceParameter(uriModified, TurSNParamType.AUTO_CORRECTION_DISABLED, TRUE);
		}
		this.text = text;
		this.link = uriModified.toString();
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

}
