/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.connector.wem.beans;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
public class TuringTag {
	@Getter
    private String tagName;
	@Getter
    private List<String> srcAttributeRelation;
	@Getter
    private String srcAttributeType;
	@Getter
    private String srcXmlName;
	@Getter
    private String srcClassName;
	private boolean srcMandatory;
	private boolean srcUniqueValues;
	@Getter
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

    public boolean getSrcMandatory() {
		return srcMandatory;
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

}
