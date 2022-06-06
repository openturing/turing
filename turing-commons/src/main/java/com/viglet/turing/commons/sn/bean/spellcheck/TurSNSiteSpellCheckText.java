/*
 * Copyright (C) 2016-2021 the original author or authors. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.turing.commons.sn.bean.spellcheck;

import java.net.URI;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.commons.utils.TurCommonsUtils;

/**
 * Spell Check Text of Turing AI Semantic Navigation response.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.5
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteSpellCheckText {
	private static final String TRUE = "1";
	private String text;
	private String link;
	
	public TurSNSiteSpellCheckText() {
		super();
	}
	
	public TurSNSiteSpellCheckText(URI uri, String text, boolean isOriginal) {
		super();
		URI uriModified = TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.QUERY, text);
		if (isOriginal) {
			uriModified = TurCommonsUtils.addOrReplaceParameter(uriModified, TurSNParamType.AUTO_CORRECTION_DISABLED, TRUE);
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
