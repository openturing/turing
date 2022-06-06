/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
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

	public boolean getIsOverridable() {
		return isOverridable;
	}

	public void setIsOverridable(boolean isOverridable) {
		this.isOverridable = isOverridable;
	}

	public boolean getIsEnum() {
		return isEnum;
	}

	public void setIsEnum(boolean isEnum) {
		this.isEnum = isEnum;
	}

	public boolean getIsRegexp() {
		return isRegexp;
	}

	public void setIsRegexp(boolean isRegexp) {
		this.isRegexp = isRegexp;
	}

	public boolean getAutomatedExpansion() {
		return automatedExpansion;
	}

	public void setAutomatedExpansion(boolean automatedExpansion) {
		this.automatedExpansion = automatedExpansion;
	}

	public boolean getAllowFuzzyExtraction() {
		return allowFuzzyExtraction;
	}

	public void setAllowFuzzyExtraction(boolean allowFuzzyExtraction) {
		this.allowFuzzyExtraction = allowFuzzyExtraction;
	}

}
