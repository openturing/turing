/*
 * Copyright (C) 2019 the original author or authors. 
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

package com.viglet.turing.converse.exchange.entity;

public class TurConverseEntityExchange {

	private String id;

	private String name;

	private boolean isOverridable;

	private boolean isEnum;

	private boolean isRegexp;

	private boolean automatedExpansion;

	private boolean allowFuzzyExtraction;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isOverridable() {
		return isOverridable;
	}

	public void setOverridable(boolean isOverridable) {
		this.isOverridable = isOverridable;
	}

	public boolean isEnum() {
		return isEnum;
	}

	public void setEnum(boolean isEnum) {
		this.isEnum = isEnum;
	}

	public boolean isRegexp() {
		return isRegexp;
	}

	public void setRegexp(boolean isRegexp) {
		this.isRegexp = isRegexp;
	}

	public boolean isAutomatedExpansion() {
		return automatedExpansion;
	}

	public void setAutomatedExpansion(boolean automatedExpansion) {
		this.automatedExpansion = automatedExpansion;
	}

	public boolean isAllowFuzzyExtraction() {
		return allowFuzzyExtraction;
	}

	public void setAllowFuzzyExtraction(boolean allowFuzzyExtraction) {
		this.allowFuzzyExtraction = allowFuzzyExtraction;
	}

}
