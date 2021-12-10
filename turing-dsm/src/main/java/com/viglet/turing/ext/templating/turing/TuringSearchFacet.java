package com.viglet.turing.ext.templating.turing;

import com.viglet.turing.ext.templating.turing.util.TemplatingUtil;

public class TuringSearchFacet {
	private String link;
	private String thumbnail;
	private String name;
	private String count;



	public String getCount() {
		return count;
	}

	public void setCount(String count) {
		this.count = count;
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
