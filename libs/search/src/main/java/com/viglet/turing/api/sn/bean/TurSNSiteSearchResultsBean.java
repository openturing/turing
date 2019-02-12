package com.viglet.turing.api.sn.bean;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TurSNSiteSearchResultsBean {

	private List<TurSNSiteSearchDocumentBean> document;

	public List<TurSNSiteSearchDocumentBean> getDocument() {
		return document;
	}

	public void setDocument(List<TurSNSiteSearchDocumentBean> document) {
		this.document = document;
	}

}
