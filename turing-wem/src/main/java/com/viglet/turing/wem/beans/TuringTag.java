/*
 * Copyright (C) 2016-2021 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
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
