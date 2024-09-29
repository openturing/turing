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

import java.lang.invoke.MethodHandles;

import com.viglet.turing.connector.wem.config.IHandlerConfiguration;
import com.viglet.turing.connector.wem.mappers.MappingDefinitions;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

public class TurAttrDefContext {
	private static final ContextLogger logger = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());
	
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
