package com.viglet.turing.se.facet;

public class VigSEFacetMap {
	String facet;
	String alias;
	String internal;
	String rdf;

	public VigSEFacetMap(String facet, String alias, String internal, String rdf) {
		this.setFacet(facet);
		this.setAlias(alias);
		this.setInternal(internal);
		this.setRdf(rdf);
	}

	public String getRdf() {
		return rdf;
	}

	public void setRdf(String rdf) {
		this.rdf = rdf;
	}

	public String getFacet() {
		return facet;
	}

	public void setFacet(String facet) {
		this.facet = facet;
	}

	public String getAlias() {
		return alias;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	public String getInternal() {
		return internal;
	}

	public void setInternal(String internal) {
		this.internal = internal;
	}

}
