/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
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
