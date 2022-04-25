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
package com.viglet.turing.client.sn.didyoumean;

import com.viglet.turing.api.sn.bean.spellcheck.TurSNSiteSpellCheckText;

/**
 * Did You Mean Text Class.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.5
 */
public class TurSNDidYouMeanText {
	private String text;
	private String link;

	public TurSNDidYouMeanText() {
		super();
	}
	
	public TurSNDidYouMeanText(TurSNSiteSpellCheckText turSNSiteSpellCheckText) {
		super();
		this.text = turSNSiteSpellCheckText.getText();
		this.link = turSNSiteSpellCheckText.getLink();
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
