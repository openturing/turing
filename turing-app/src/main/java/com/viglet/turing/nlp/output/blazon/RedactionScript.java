package com.viglet.turing.nlp.output.blazon;

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "RedactionScript")
public class RedactionScript {

	@XmlAttribute
	private String version;
	@XmlElement(name = "RedactionCommand")
	private List<RedactionCommand> redactionCommands;

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public List<RedactionCommand> getRedactionCommands() {
		return redactionCommands;
	}

	public void setRedactionCommands(List<RedactionCommand> redactionCommands) {
		this.redactionCommands = redactionCommands;
	}

}
