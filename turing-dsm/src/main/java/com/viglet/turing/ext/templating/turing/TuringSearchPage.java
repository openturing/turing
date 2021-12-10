package com.viglet.turing.ext.templating.turing;

import com.viglet.turing.ext.templating.turing.util.TemplatingUtil;

public class TuringSearchPage {
	private String link;
	private String thumbnail;
	private String name;
	private String pageNumber;



	public String getPageNumber() {
		return pageNumber;
	}

	public void setPageNumber(String pageNumber) {
		this.pageNumber = pageNumber;
	}

	public String getLink()
	{
		return this.link;
	}

	public void setLink(String link)
	{
		this.link = link;
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
