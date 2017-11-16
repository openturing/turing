package com.viglet.turing.api.sn.bean;

import java.util.List;
import java.util.Map;


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
