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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viglet.turing.connector.cms.beans.TurCTDMappingMap;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.index.IValidToIndex;

public class MappingDefinitions {
	private TurCTDMappingMap turCTDMappingMap;
	private String mappingsXMLFile;
	private static Logger logger = LoggerFactory.getLogger(MappingDefinitions.class);

	public String getMappingsXML() {
		return mappingsXMLFile;
	}

	public void setMappingsXML(String mappingsXML) {
		this.mappingsXMLFile = mappingsXML;
	}

	public MappingDefinitions() {
	}

	public MappingDefinitions(String mappingsXML, TurCTDMappingMap turCTDMappingMap) {
		if (logger.isDebugEnabled()) {
			logger.debug("initializing mapping definitions");
		}
		setMappingsXML(mappingsXML);
		setMappingDefinitions(turCTDMappingMap);
	}

	public TurCTDMappingMap getMappingDefinitions() {
		return turCTDMappingMap;
	}

	public void setMappingDefinitions(TurCTDMappingMap turCTDMappingMap) {
		this.turCTDMappingMap = turCTDMappingMap;
	}

	public boolean hasContentType(String contentTypeName) {
		return (turCTDMappingMap.get(contentTypeName) != null);
	}
	public CTDMappings getMappingByContentType(String contentTypeName) {
		return turCTDMappingMap.get(contentTypeName);
	}
	public boolean hasClassValidToIndex(String contentTypeName) {
		CTDMappings ctdMappings = turCTDMappingMap.get(contentTypeName);
		boolean status = (ctdMappings != null && ctdMappings.getClassValidToIndex() != null);
		if (!status && logger.isDebugEnabled())
			logger.debug(String.format("Valid to Index className is not found in the mappingXML for the CTD: %s",
					contentTypeName));
		return status;
	}

	public IValidToIndex validToIndex(Object ot, IHandlerConfiguration config) {

		try {
			String contentTypeName;
			contentTypeName = null;

			if (this.hasClassValidToIndex(contentTypeName)) {
				CTDMappings ctdMappings = turCTDMappingMap.get(contentTypeName);
				IValidToIndex instance = null;
				String className = ctdMappings.getClassValidToIndex();
				if (className != null) {
					Class<?> clazz = Class.forName(className);

					if (clazz == null) {
						if (logger.isDebugEnabled())
							logger.debug(String.format("Valid to Index className is not found in the jar file: %s",
									className));

					} else
						instance = (IValidToIndex) clazz.newInstance();
				}
				return instance;
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;

	}

	public boolean isClassValidToIndex(Object ci, IHandlerConfiguration config) {
		
		return false;
	}
}
