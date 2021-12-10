package com.viglet.turing.ext.templating.turing;

import java.util.List;

import com.viglet.turing.ext.templating.turing.util.TemplatingUtil;


public class TuringSearchFacetType {
	private String name;
	private String thumbnail;
	private List<TuringSearchFacet> facets;
	private boolean applied;
	
	public List<TuringSearchFacet> getFacets() {
		return facets;
	}

	public void setFacets(List<TuringSearchFacet> facets) {
		this.facets = facets;
	}

	public boolean isApplied() {
		return applied;
	}

	public void setApplied(boolean applied) {
		this.applied = applied;
	}

	public String getThumbnail()
	{
		return this.thumbnail;
	}

	public void setThumbnail(String thumbnail)
	{
		this.thumbnail = thumbnail;
	}

	public String getName()
	{
		return this.name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public String toString()
	{
		return TemplatingUtil.getToString(this);
	}
}
