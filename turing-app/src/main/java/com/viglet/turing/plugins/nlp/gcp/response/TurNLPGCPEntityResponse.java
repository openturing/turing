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
package com.viglet.turing.plugins.nlp.gcp.response;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
@Getter
public class TurNLPGCPEntityResponse {

	private String name;

	private TurNLPGCPEntityTypeResponse type;

	private Map<String, Object> metadata;

	private double salience;

	private List<TurNLPCGCPMentionResponse> mentions;

	public void setName(String name) {
		this.name = name;
	}

	public void setType(TurNLPGCPEntityTypeResponse type) {
		this.type = type;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata;
	}

	public void setSalience(double salience) {
		this.salience = salience;
	}

	public void setMentions(List<TurNLPCGCPMentionResponse> mentions) {
		this.mentions = mentions;
	}

}
