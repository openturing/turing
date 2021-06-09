/*
 * Copyright (C) 2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.converse.exchange.intent;

import java.util.List;

public class TurConverseIntentResponseParameterExchange {

	private String id;

	private boolean required;

	private String dataType;

	private String name;

	private String value;

	private List<String> promptMessages;

	private List<String> noMatchPromptMessages;

	private List<String> noInputPromptMessages;

	private List<String> outputDialogContexts;

	private String defaultValue;

	private boolean isList;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public boolean isRequired() {
		return required;
	}

	public void setRequired(boolean required) {
		this.required = required;
	}

	public String getDataType() {
		return dataType;
	}

	public void setDataType(String dataType) {
		this.dataType = dataType;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

	public List<String> getPromptMessages() {
		return promptMessages;
	}

	public void setPromptMessages(List<String> promptMessages) {
		this.promptMessages = promptMessages;
	}

	public List<String> getNoMatchPromptMessages() {
		return noMatchPromptMessages;
	}

	public void setNoMatchPromptMessages(List<String> noMatchPromptMessages) {
		this.noMatchPromptMessages = noMatchPromptMessages;
	}

	public List<String> getNoInputPromptMessages() {
		return noInputPromptMessages;
	}

	public void setNoInputPromptMessages(List<String> noInputPromptMessages) {
		this.noInputPromptMessages = noInputPromptMessages;
	}

	public List<String> getOutputDialogContexts() {
		return outputDialogContexts;
	}

	public void setOutputDialogContexts(List<String> outputDialogContexts) {
		this.outputDialogContexts = outputDialogContexts;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	public boolean getIsList() {
		return isList;
	}

	public void setIsList(boolean isList) {
		this.isList = isList;
	}

}
