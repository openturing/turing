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
package com.viglet.turing.connector.cms.mappers;

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
