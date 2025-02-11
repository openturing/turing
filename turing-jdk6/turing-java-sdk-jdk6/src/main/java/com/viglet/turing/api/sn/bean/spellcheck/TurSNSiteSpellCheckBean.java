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
package com.viglet.turing.api.sn.bean.spellcheck;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Spell Check of Turing AI Semantic Navigation response.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.5
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteSpellCheckBean {

	private boolean correctedText;
	private boolean usingCorrectedText;
	private TurSNSiteSpellCheckText original;
	private TurSNSiteSpellCheckText corrected;

	public TurSNSiteSpellCheckText getOriginal() {
		return original;
	}

	public void setOriginal(TurSNSiteSpellCheckText original) {
		this.original = original;
	}

	public TurSNSiteSpellCheckText getCorrected() {
		return corrected;
	}

	public void setCorrected(TurSNSiteSpellCheckText corrected) {
		this.corrected = corrected;
	}

	public boolean isCorrectedText() {
		return correctedText;
	}

	public void setCorrectedText(boolean correctedText) {
		this.correctedText = correctedText;
	}

	public boolean isUsingCorrectedText() {
		return usingCorrectedText;
	}

	public void setUsingCorrectedText(boolean usingCorrectedText) {
		this.usingCorrectedText = usingCorrectedText;
	}

}