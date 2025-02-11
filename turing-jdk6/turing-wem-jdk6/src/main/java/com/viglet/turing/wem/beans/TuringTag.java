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
package com.viglet.turing.wem.beans;

import java.util.List;

public class TuringTag {
	private String tagName;
	private List<String> srcAttributeRelation;
	private String srcAttributeType;
	private String srcXmlName;
	private String srcClassName;
	private boolean srcMandatory;
	private boolean srcUniqueValues;
	private String textValue;

	@Override
	public String toString() {
		return String.format(
				"tagName: %s, srcXmlName %s, srcAttrRelation: %s, srcAttrType: %s, className: %s, mandatory: %s",
				this.getTagName(), this.getSrcXmlName(), this.getSrcAttributeRelation(), this.getSrcAttributeType(),
				this.getSrcClassName(), this.getSrcMandatory());
	}

	public TuringTag() {
		super();
	}

	public String getSrcAttributeType() {
		return srcAttributeType;
	}

	public List<String> getSrcAttributeRelation() {
		return srcAttributeRelation;
	}

	public String getSrcClassName() {
		return srcClassName;
	}

	public boolean getSrcMandatory() {
		return srcMandatory;
	}

	public String getTagName() {
		return tagName;
	}

	public String getTextValue() {
		return textValue;
	}

	public void setSrcAttributeType(String srcAttributeType) {
		this.srcAttributeType = srcAttributeType;
	}

	public void setSrcAttributeRelation(List<String> srcAttributeRelation) {
		this.srcAttributeRelation = srcAttributeRelation;
	}

	public void setSrcClassName(String srcClassName) {
		this.srcClassName = srcClassName;
	}

	public void setSrcMandatory(boolean srcMandatory) {
		this.srcMandatory = srcMandatory;
	}

	public void setTagName(String tagName) {
		this.tagName = tagName;
	}

	public String getSrcXmlName() {
		return srcXmlName;
	}

	public void setSrcXmlName(String srcXmlName) {
		this.srcXmlName = srcXmlName;
	}

	public void setTextValue(String textValue) {
		this.textValue = textValue;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TuringTag) {
			return this.getTagName().equals(((TuringTag) obj).getTagName());
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return this.getTagName().hashCode();
	}

	public boolean isSrcUniqueValues() {
		return srcUniqueValues;
	}

	public void setSrcUniqueValues(boolean srcUniqueValues) {
		this.srcUniqueValues = srcUniqueValues;
	}
}
