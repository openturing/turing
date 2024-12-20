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
public class TurAttrDef {
	@Getter
    private String tagName;
	private TurMultiValue multiValue;

	public TurAttrDef (String tagName, TurMultiValue multiValue) {
		this.tagName = tagName;
		this.multiValue = multiValue;
	}

    public List<String> getMultiValue() {
		return multiValue;
	}

    public String toString() {
	    return String.format("tagName: %s, multiValue: %s", tagName, multiValue);
	} 
}
