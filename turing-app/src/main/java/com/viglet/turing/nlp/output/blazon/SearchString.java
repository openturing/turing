package com.viglet.turing.nlp.output.blazon;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "SearchString")
public class SearchString {
	@XmlAttribute
	private String string;
	@XmlAttribute
	private boolean matchWholeWord;
	
	public String getString() {
		return string;
	}
	public void setString(String string) {
		this.string = string;
	}
	public boolean isMatchWholeWord() {
		return matchWholeWord;
	}
	public void setMatchWholeWord(boolean matchWholeWord) {
		this.matchWholeWord = matchWholeWord;
	}
	
	
}
