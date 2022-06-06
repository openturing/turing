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
