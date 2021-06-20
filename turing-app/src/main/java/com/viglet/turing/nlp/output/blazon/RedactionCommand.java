package com.viglet.turing.nlp.output.blazon;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "RedactionCommand")
public class RedactionCommand {
	
	@XmlAttribute
	private String comment;
	@XmlElement(name = "SearchString")
	private SearchString searchString;
	public String getComment() {
		return comment;
	}
	public void setComment(String comment) {
		this.comment = comment;
	}
	public SearchString getSearchString() {
		return searchString;
	}
	public void setSearchString(SearchString searchString) {
		this.searchString = searchString;
	}
	
}
