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

package com.viglet.turing.converse.exchange.intent;

import java.util.List;

public class TurConverseIntentPhraseExchange {

	private String id;

	private List<TurConverseIntentPhraseDataExchange> data;

	private boolean isTemplate;

	private int count;

	private int updated;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<TurConverseIntentPhraseDataExchange> getData() {
		return data;
	}

	public void setData(List<TurConverseIntentPhraseDataExchange> data) {
		this.data = data;
	}

	public boolean getIsTemplate() {
		return isTemplate;
	}

	public void setIsTemplate(boolean isTemplate) {
		this.isTemplate = isTemplate;
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public int getUpdated() {
		return updated;
	}

	public void setUpdated(int updated) {
		this.updated = updated;
	}

}
