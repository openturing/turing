package com.viglet.turing.api.sn.bean;

import java.util.List;

public class TurSNSiteSearchWidgetBean {

	private List<TurSNSiteSearchFacetBean> facet;
	private TurSNSiteSearchFacetBean facetToRemove;
	private List<TurSNSiteSearchSimilarBean> similar;
	
	public List<TurSNSiteSearchFacetBean> getFacet() {
		return facet;
	}
	public void setFacet(List<TurSNSiteSearchFacetBean> facet) {
		this.facet = facet;
	}
	public TurSNSiteSearchFacetBean getFacetToRemove() {
		return facetToRemove;
	}
	public void setFacetToRemove(TurSNSiteSearchFacetBean facetToRemove) {
		this.facetToRemove = facetToRemove;
	}
	public List<TurSNSiteSearchSimilarBean> getSimilar() {
		return similar;
	}
	public void setSimilar(List<TurSNSiteSearchSimilarBean> similar) {
		this.similar = similar;
	}

	

	

}
