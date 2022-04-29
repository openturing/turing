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

import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;

/**
 * Return the correct text of Turing AI response.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.5
 */
public class TurSNDidYouMean {
	private boolean correctedText = true;
	private TurSNDidYouMeanText original;
	private TurSNDidYouMeanText corrected;

	public TurSNDidYouMean() {
		super();
		this.correctedText = true;
		this.original = new TurSNDidYouMeanText();
		this.corrected = new TurSNDidYouMeanText();
	}

	public TurSNDidYouMean(TurSNSiteSpellCheckBean turSNSiteSpellCheckBean) {
		super();
		this.correctedText = turSNSiteSpellCheckBean.isCorrectedText();
		this.original = new TurSNDidYouMeanText(turSNSiteSpellCheckBean.getOriginal());
		this.corrected = new TurSNDidYouMeanText(turSNSiteSpellCheckBean.getCorrected());
	}

	public TurSNDidYouMeanText getOriginal() {
		return original;
	}

	public void setOriginal(TurSNDidYouMeanText original) {
		this.original = original;
	}

	public TurSNDidYouMeanText getCorrected() {
		return corrected;
	}

	public void setCorrected(TurSNDidYouMeanText corrected) {
		this.corrected = corrected;
	}

	public boolean isCorrectedText() {
		return correctedText;
	}

	public void setCorrectedText(boolean correctedText) {
		this.correctedText = correctedText;
	}

}
