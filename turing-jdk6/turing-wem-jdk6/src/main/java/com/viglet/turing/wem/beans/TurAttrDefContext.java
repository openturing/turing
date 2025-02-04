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

import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.mappers.MappingDefinitions;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

public class TurAttrDefContext {
	private static final ContextLogger logger = ContextLogger.getLogger(TurAttrDefContext.class.getName());
	
	private ContentInstance contentInstance;
	private TuringTag turingTag;
	private IHandlerConfiguration iHandlerConfiguration;
	private MappingDefinitions mappingDefinitions;
	private AttributeData attributeData;

	public TurAttrDefContext(TurAttrDefContext turAttrDefContext) {
		this.setAttributeData(turAttrDefContext.getAttributeData());
		this.setContentInstance(turAttrDefContext.getContentInstance());
		this.setiHandlerConfiguration(turAttrDefContext.getiHandlerConfiguration());
		this.setMappingDefinitions(turAttrDefContext.getMappingDefinitions());
		this.setTuringTag(turAttrDefContext.getTuringTag());
	}

	public TurAttrDefContext(ContentInstance contentInstance, TuringTag turingTag,
			IHandlerConfiguration iHandlerConfiguration, MappingDefinitions mappingDefinitions) {
		this.setContentInstance(contentInstance);
		this.setiHandlerConfiguration(iHandlerConfiguration);
		this.setMappingDefinitions(mappingDefinitions);
		this.setTuringTag(turingTag);
	}

	public ContentInstance getContentInstance() {
		return contentInstance;
	}

	public void setContentInstance(ContentInstance contentInstance) {
		this.contentInstance = contentInstance;
	}

	public TuringTag getTuringTag() {
		return turingTag;
	}

	public void setTuringTag(TuringTag turingTag) {
		this.turingTag = turingTag;
	}

	public IHandlerConfiguration getiHandlerConfiguration() {
		return iHandlerConfiguration;
	}

	public void setiHandlerConfiguration(IHandlerConfiguration iHandlerConfiguration) {
		this.iHandlerConfiguration = iHandlerConfiguration;
	}

	public MappingDefinitions getMappingDefinitions() {
		return mappingDefinitions;
	}

	public void setMappingDefinitions(MappingDefinitions mappingDefinitions) {
		this.mappingDefinitions = mappingDefinitions;
	}

	public AttributeData getAttributeData() {
		if (attributeData != null)
			return attributeData;

		try {
			return contentInstance.getAttribute(turingTag.getSrcXmlName());
		} catch (ApplicationException e) {
			logger.error(e);
		}

		return null;
	}

	public void setAttributeData(AttributeData attributeData) {
		this.attributeData = attributeData;
	}
}
