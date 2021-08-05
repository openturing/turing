/*
 * Copyright (C) 2016-2019 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
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
package com.viglet.turing.wem.mappers;

public class TurXMLConstant {

	private TurXMLConstant() {
		throw new IllegalStateException("TurXMLConstant");
	}

	public static final String VALUE_TYPE_ATT = "valueType";
	public static final String XML_NAME_ATT = "xmlName";
	public static final String CLASS_NAME_ATT = "className";
	public static final String RELATION_ATT = "relation";
	public static final String MANDATORY_ATT = "mandatory";
	public static final String TAG_COMMON_INDEX_DATA = "common-index-attrs";
	public static final String TAG_INDEX_DATA = "index-attrs";
	public static final String TAG_MAPPING_DEF = "mappingDefinition";
	public static final String TAG_ATT_MAPPING_DEF = "contentType";
	public static final String TAG_ATT_CUSTOM_CLASS = "customClassName";
	public static final String TAG_ATT_CLASS_VALID_TOINDEX = "validToIndex";
	public static final String UNIQUE_VALUES_ATT = "uniqueValues";
	public static final String TEXT_VALUE_ATT = "textValue";
}
