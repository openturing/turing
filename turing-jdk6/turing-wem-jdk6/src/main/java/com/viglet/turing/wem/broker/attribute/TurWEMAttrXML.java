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
package com.viglet.turing.wem.broker.attribute;

import com.viglet.turing.wem.beans.TurAttrDef;
import com.viglet.turing.wem.beans.TurAttrDefContext;
import com.viglet.turing.wem.beans.TurMultiValue;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.broker.relator.TurWEMRelator;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.javabean.AttributedObject;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;


import java.util.ArrayList;
import java.util.List;

public class TurWEMAttrXML {
	private static final ContextLogger log = ContextLogger.getLogger(TurWEMAttrXML.class.getName());

	private TurWEMAttrXML() {
		throw new IllegalStateException("TurWEMAttrXML");
	}

	public static List<TurAttrDef> attributeXML(TurAttrDefContext turAttrDefContext) throws Exception {
		TuringTag turingTag = turAttrDefContext.getTuringTag();
		if (turingTag.getTextValue() != null && !turingTag.getTextValue().isEmpty()) {
			List<TurAttrDef> attributesDefs = new ArrayList<TurAttrDef>();
			TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(),
					TurMultiValue.singleItem(turingTag.getTextValue()));
			attributesDefs.add(turAttrDef);
			return attributesDefs;
		} else {
			if (log.isDebugEnabled()) {
				log.debug(
						String.format("attributeXML getSrcAttributeRelation(): %s", turingTag.getSrcAttributeRelation()));
				log.debug(String.format("attributeXML getSrcXmlName(): %s", turingTag.getSrcXmlName()));
			}
			if (turingTag.getSrcAttributeRelation() != null && !turingTag.getSrcAttributeRelation().isEmpty()
					&& !turingTag.getSrcXmlName().equals(turingTag.getSrcAttributeRelation().get(0)))
				return addAttributeWithRelator(turAttrDefContext);
			else
				return addAttributeWithoutRelator(turAttrDefContext);
		}
	}

	public static List<TurAttrDef> attributeXMLUpdate(TurAttrDefContext turAttrDefContext, AttributeData attributeData)
			throws Exception {
		TuringTag turingTag = turAttrDefContext.getTuringTag();
		if (log.isDebugEnabled() && attributeData != null)
			log.debug(String.format("%s = %s", turingTag.getTagName(), attributeData.getValue().toString()));

		if (attributeData != null && attributeData.getValue().toString() != null
				&& attributeData.getValue().toString().trim().length() > 0)
			return TurWEMAttrWidget.attributeByWidget(turAttrDefContext, attributeData);

		return new ArrayList<TurAttrDef>();
	}

	private static List<TurAttrDef> addAttributeWithRelator(TurAttrDefContext turAttrDefContext) throws Exception {
		TuringTag turingTag = turAttrDefContext.getTuringTag();
		ContentInstance ci = turAttrDefContext.getContentInstance();
		String attributeName = turAttrDefContext.getTuringTag().getSrcXmlName();
		AttributedObject[] relation = ci.getRelations(turingTag.getSrcAttributeRelation().get(0));
		List<TurAttrDef> attributesDefs = new ArrayList<TurAttrDef>();

		relation = getRelationIfExists(turingTag, relation);
		addRelationAttributes(turAttrDefContext, attributeName, relation, attributesDefs);

		return attributesDefs;
	}

	private static AttributedObject[] getRelationIfExists(TuringTag turingTag, AttributedObject[] relation) {
		if (turingTag.getSrcAttributeRelation().size() > 1) {
			log.debug("Attribute has nested relator");
			List<AttributedObject[]> nestedRelation = new ArrayList<AttributedObject[]>();
			nestedRelation.add(relation);
			relation = TurWEMRelator.nestedRelators(turingTag.getSrcAttributeRelation(), nestedRelation, 0);
		}
		return relation;
	}

	private static void addRelationAttributes(TurAttrDefContext turAttrDefContext, String attributeName,
			AttributedObject[] relation, List<TurAttrDef> attributesDefs) throws Exception {
		if (relation != null) {
			for (AttributedObject attributedObject : relation) {
				if (attributedObject.getAttributeValue(attributeName) != null) {
					String attributeValue = attributedObject.getAttributeValue(attributeName).toString();
					AttributeData attributeData = attributedObject.getAttribute(attributeName);
					if (log.isDebugEnabled())
						log.debug(String.format("Attribute: %s,  Value: %s", attributeName, attributeValue));
					if (attributeValue != null && attributeValue.trim().length() > 0)
						attributesDefs.addAll(attributeXMLUpdate(turAttrDefContext, attributeData));
				}
			}
		}
	}

	private static List<TurAttrDef> addAttributeWithoutRelator(TurAttrDefContext turAttrDefContext) throws Exception {
		TuringTag turingTag = turAttrDefContext.getTuringTag();
		ContentInstance ci = turAttrDefContext.getContentInstance();
		String attributeName = turAttrDefContext.getTuringTag().getSrcXmlName();
		if (ci.getAttributeValue(attributeName) != null
				&& ci.getAttributeValue(attributeName).toString().trim().length() > 0) {
			AttributeData attributeData = ci.getAttribute(attributeName);
			return attributeXMLUpdate(turAttrDefContext, attributeData);
		} else if (turingTag.getSrcClassName() != null) {
			AttributeData attributeData = ci.getAttribute(attributeName);
			return TurWEMAttrClass.attributeByClass(turAttrDefContext, attributeData);
		} else {
			return new ArrayList<TurAttrDef>();
		}
	}

}
