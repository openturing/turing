/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.bean.ml.sentence;

import org.springframework.stereotype.Component;

@Component
public class TurMLSentenceBean {
	private String sentence;
	
	private int turMLCategoryId;
	
	public String getSentence() {
		return sentence;
	}

	public void setSentence(String sentence) {
		this.sentence = sentence;
	}

	public int getTurMLCategoryId() {
		return turMLCategoryId;
	}

	public void setTurMLCategoryId(int turMLCategoryId) {
		this.turMLCategoryId = turMLCategoryId;
	}



}
