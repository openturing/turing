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

package com.viglet.turing.api.sn.bean;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
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
