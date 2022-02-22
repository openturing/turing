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

package com.viglet.turing.api.sn.bean;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Documents of results of Turing AI Semantic Navigation response.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.4
 */

@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteSearchDocumentBean {
	
	private String source;
	private boolean elevate;
	private List<TurSNSiteSearchDocumentMetadataBean> metadata;
	private Map<String,Object> fields;
	
	public boolean getElevate() {
		return elevate;
	}
	public void setElevate(boolean elevate) {
		this.elevate = elevate;
	}
	public List<TurSNSiteSearchDocumentMetadataBean> getMetadata() {
		return metadata;
	}
	public void setMetadata(List<TurSNSiteSearchDocumentMetadataBean> metadata) {
		this.metadata = metadata;
	}
	public Map<String, Object> getFields() {
		return fields;
	}
	public void setFields(Map<String, Object> fields) {
		this.fields = fields;
	}
	public String getSource() {
		return source;
	}
	public void setSource(String source) {
		this.source = source;
	}

}
