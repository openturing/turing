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
package com.viglet.turing.connector.wem.mappers;

import java.lang.invoke.MethodHandles;

import com.viglet.turing.connector.wem.beans.TurCTDMappingMap;
import com.viglet.turing.connector.wem.config.IHandlerConfiguration;
import com.viglet.turing.connector.wem.index.IValidToIndex;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ObjectType;
import com.vignette.logging.context.ContextLogger;
import lombok.Setter;

public class MappingDefinitions {
	private TurCTDMappingMap turCTDMappingMap;
	@Setter
    private String mappingsXML;
	private static final ContextLogger logger = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());

	public String getMappingsXML() {
		return mappingsXML;
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

	public boolean hasClassValidToIndex(String contentTypeName) {
		CTDMappings ctdMappings = turCTDMappingMap.get(contentTypeName);
		boolean status = (ctdMappings != null && ctdMappings.getClassValidToIndex() != null);
		if (!status && logger.isDebugEnabled())
			logger.debug(String.format("Valid to Index className is not found in the mappingXML for the CTD: %s",
					contentTypeName));
		return status;
	}

	public IValidToIndex validToIndex(ObjectType ot) {
		try {
			String contentTypeName;
			contentTypeName = ot.getData().getName();
			if (this.hasClassValidToIndex(contentTypeName)) {
				CTDMappings ctdMappings = turCTDMappingMap.get(contentTypeName);
				IValidToIndex instance = null;
				String className = ctdMappings.getClassValidToIndex();
				if (className != null) {
                    instance = (IValidToIndex) Class.forName(className).getDeclaredConstructor().newInstance();
				}
				return instance;
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return null;

	}

	public boolean isContentValidToIndex(ContentInstance ci, IHandlerConfiguration config) {
		try {
			IValidToIndex iValidToIndex = validToIndex(ci.getObjectType());
			return !(iValidToIndex != null && !iValidToIndex.isValid(ci, config));
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return false;
	}
}
