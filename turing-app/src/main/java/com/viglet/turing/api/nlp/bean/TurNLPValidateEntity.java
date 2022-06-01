package com.viglet.turing.api.nlp.bean;

import java.util.List;

public class TurNLPValidateEntity {
	private String name;

	private List<String> types;

	private List<String> subTypes;

	public TurNLPValidateEntity() {
		super();

	}

	public TurNLPValidateEntity(String name, List<String> types, List<String> subTypes) {
		super();
		this.name = name;
		this.types = types;
		this.subTypes = subTypes;
	}

	public TurNLPValidateEntity(String name) {
		this(name, null, null);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<String> getTypes() {
		return types;
	}

	public void setTypes(List<String> types) {
		this.types = types;
	}

	public List<String> getSubTypes() {
		return subTypes;
	}

	public void setSubTypes(List<String> subTypes) {
		this.subTypes = subTypes;
	}
}
