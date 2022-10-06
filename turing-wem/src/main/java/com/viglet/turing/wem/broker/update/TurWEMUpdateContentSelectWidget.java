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
package com.viglet.turing.wem.broker.update;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import com.viglet.turing.wem.beans.TurAttrDef;
import com.viglet.turing.wem.beans.TurAttrDefContext;
import com.viglet.turing.wem.beans.TurCTDMappingMap;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.broker.attribute.TurWEMAttrXML;
import com.viglet.turing.wem.mappers.CTDMappings;
import com.viglet.turing.wem.mappers.MappingDefinitions;
import com.viglet.turing.wem.util.TuringUtils;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.logging.context.ContextLogger;

public class TurWEMUpdateContentSelectWidget {
	private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());

	private TurWEMUpdateContentSelectWidget() {
		throw new IllegalStateException("TurWEMUpdateContentSelectWidget");
	}
	
	public static List<TurAttrDef> attributeContentSelectUpdate(TurAttrDefContext turAttrDefContext,
			AttributeData attributeData) throws Exception {

		MappingDefinitions mappingDefinitions = turAttrDefContext.getMappingDefinitions();
		List<TurAttrDef> attributesDefs = new ArrayList<>();

		ContentInstance ciRelated = (ContentInstance) ManagedObject
				.findByContentManagementId(new ManagedObjectVCMRef(attributeData.getValue().toString()));
		
		attributesDefs = processContentInstanceRelated(turAttrDefContext, mappingDefinitions, attributesDefs,
				ciRelated);
		
		return attributesDefs;
	}

	private static List<TurAttrDef> processContentInstanceRelated(TurAttrDefContext turAttrDefContext,
			MappingDefinitions mappingDefinitions, List<TurAttrDef> attributesDefs, ContentInstance ciRelated)
			throws Exception {
		if (ciRelated != null) {
			String contentTypeName = ciRelated.getObjectType().getData().getName();
			if (log.isDebugEnabled())
				log.debug(String.format("CTD Related: %s", contentTypeName));

			// we force the type on the Viglet Turing side
			TurCTDMappingMap relatedMappings = mappingDefinitions.getMappingDefinitions();

			CTDMappings ctdRelatedMappings = relatedMappings.get(contentTypeName);

			if (ctdRelatedMappings == null) {
				log.error(String.format("Mapping definition is not found in the mappingXML for the CTD: %s",
						contentTypeName));
			} else {
				attributesDefs = processURLFromRelation(turAttrDefContext, attributesDefs, ciRelated,
						ctdRelatedMappings);
			}
		}
		return attributesDefs;
	}

	private static List<TurAttrDef> processURLFromRelation(TurAttrDefContext turAttrDefContext,
			List<TurAttrDef> attributesDefs, ContentInstance ciRelated, CTDMappings ctdRelatedMappings)
			throws Exception {
		// Process URL from Relation.
		for (String tag : ctdRelatedMappings.getTagList()) {
			TurAttrDefContext turAttrDefContextRelated = new TurAttrDefContext(turAttrDefContext);
			turAttrDefContextRelated.setContentInstance(ciRelated);
			for (TuringTag tagRelated : ctdRelatedMappings.getTuringTagMap().get(tag)) {
				if (tag != null && tagRelated != null && tagRelated.getTagName() != null
						&& tagRelated.getTagName().equals("url")) {
					if (log.isDebugEnabled())
						log.debug(String.format(
								"Key Related: %s,  Tag Related: %s, relation: %s, content Type: %s ", tag,
								tagRelated.getTagName(),
								TuringUtils.listToString(tagRelated.getSrcAttributeRelation()),
								tagRelated.getSrcAttributeType()));
					attributesDefs = TurWEMAttrXML.attributeXML(turAttrDefContextRelated);
				}

			}
		}
		return attributesDefs;
	}
}
