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
package com.viglet.turing.plugins.nlp.gcp.request;

import lombok.Getter;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
@Getter
public class TurNLPGCPDocumentRequest {

	private TurNLPGCPTypeResponse type;

	private String language;

	private String content;
	
	private String gcsContentUri;

	public TurNLPGCPDocumentRequest(TurNLPGCPTypeResponse type, String language, String content) {
		super();
		this.type = type;
		this.language = language;
		this.content = content;
	}

	public void setType(TurNLPGCPTypeResponse type) {
		this.type = type;
	}

	public void setLanguage(String language) {
		this.language = language;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public void setGcsContentUri(String gcsContentUri) {
		this.gcsContentUri = gcsContentUri;
	}

	
}
