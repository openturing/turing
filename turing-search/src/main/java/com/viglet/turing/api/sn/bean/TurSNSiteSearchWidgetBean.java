package com.viglet.turing.api.sn.bean;

import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.turing.se.similar.TurSESimilarResult;

@Component
public class TurSNSiteSearchWidgetBean {

	private List<TurSNSiteSearchFacetBean> facet;
	private TurSNSiteSearchFacetBean facetToRemove;
	private List<TurSESimilarResult> similar;
	
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
	public List<TurSESimilarResult> getSimilar() {
		return similar;
	}
	public void setSimilar(List<TurSESimilarResult> similar) {
		this.similar = similar;
	}

	

	

}
