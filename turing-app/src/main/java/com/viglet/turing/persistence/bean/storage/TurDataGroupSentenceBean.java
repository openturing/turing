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

package com.viglet.turing.persistence.bean.storage;

import org.springframework.stereotype.Component;

@Component
public class TurDataGroupSentenceBean {

	private int id;
	private String sentence;
	private int turData;
	private int turDataGroup;
	private int turMLCategory;
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getSentence() {
		return sentence;
	}
	public void setSentence(String sentence) {
		this.sentence = sentence;
	}
	public int getTurData() {
		return turData;
	}
	public void setTurData(int turData) {
		this.turData = turData;
	}
	public int getTurDataGroup() {
		return turDataGroup;
	}
	public void setTurDataGroup(int turDataGroup) {
		this.turDataGroup = turDataGroup;
	}
	public int getTurMLCategory() {
		return turMLCategory;
	}
	public void setTurMLCategory(int turMLCategory) {
		this.turMLCategory = turMLCategory;
	}

}
