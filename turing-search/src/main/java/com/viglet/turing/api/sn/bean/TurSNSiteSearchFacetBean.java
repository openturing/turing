package com.viglet.turing.api.sn.bean;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TurSNSiteSearchFacetBean {

	private List<TurSNSiteSearchFacetItemBean> facets;
	private TurSNSiteSearchFacetLabelBean label;
	public List<TurSNSiteSearchFacetItemBean> getFacets() {
		return facets;
	}
	public void setFacets(List<TurSNSiteSearchFacetItemBean> facets) {
		this.facets = facets;
	}
	public TurSNSiteSearchFacetLabelBean getLabel() {
		return label;
	}
	public void setLabel(TurSNSiteSearchFacetLabelBean label) {
		this.label = label;
	}

}
