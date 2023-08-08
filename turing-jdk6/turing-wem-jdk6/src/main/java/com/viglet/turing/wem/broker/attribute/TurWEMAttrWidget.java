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


import java.util.ArrayList;
import java.util.List;

import com.viglet.turing.wem.beans.TurAttrDef;
import com.viglet.turing.wem.beans.TurAttrDefContext;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.broker.relator.TurWEMRelator;
import com.viglet.turing.wem.broker.update.TurWEMUpdateContentSelectWidget;
import com.viglet.turing.wem.broker.update.TurWEMUpdateFileWidget;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.javabean.AttributedObject;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

public class TurWEMAttrWidget {
	private static final ContextLogger log = ContextLogger.getLogger(TurWEMAttrWidget.class.getName());

	private TurWEMAttrWidget() {
		throw new IllegalStateException("TurWEMAttrWidget");
	}
	
	public static List<TurAttrDef> attributeByWidget(TurAttrDefContext turAttrDefContext, AttributeData attributeData)
			throws Exception {
		TuringTag turingTag = turAttrDefContext.getTuringTag();
		ContentInstance ci = turAttrDefContext.getContentInstance();
		String attributeName = turAttrDefContext.getTuringTag().getSrcXmlName();
		String widgetName = null;

		if (turingTag.getSrcAttributeRelation() != null && !turingTag.getSrcAttributeRelation().isEmpty()) {
			widgetName = relationWidgetName(turingTag, ci, attributeName, widgetName);

		} else {
			widgetName = ci.getAttribute(attributeName).getAttributeDefinition().getWidgetName();
		}

		if (widgetName != null && widgetName.equals("WCMContentSelectWidget")
				&& attributeData.getValue().toString().length() == 40 && turingTag.getSrcClassName() == null) {
			if (log.isDebugEnabled()) {
				log.debug("WCMContentSelectWidget value: " + attributeData.getValue().toString());
				log.debug("WCMContentSelectWidget length: " + attributeData.getValue().toString().length());
			}
			return TurWEMUpdateContentSelectWidget.attributeContentSelectUpdate(turAttrDefContext,
					attributeData);

		} else if (widgetName != null && widgetName.equals("VCMFileWidget") && turingTag.getSrcClassName() == null) {
			if (log.isDebugEnabled()) {
				log.debug("VCMFileWidget value: " + attributeData.getValue().toString());
				log.debug("VCMFileWidget length: " + attributeData.getValue().toString().length());
			}
			return TurWEMUpdateFileWidget.attributeFileWidgetUpdate(turAttrDefContext,
					attributeData);
		} else {
			return TurWEMAttrClass.attributeByClass(turAttrDefContext,
					attributeData);
		}

	}

	private static String relationWidgetName(TuringTag turingTag, ContentInstance ci, String attributeName,
			String widgetName) throws ApplicationException {
		AttributedObject[] relation = ci.getRelations(turingTag.getSrcAttributeRelation().get(0));

		if (turingTag.getSrcAttributeRelation().size() > 1) {
			List<AttributedObject[]> nestedRelation = new ArrayList<AttributedObject[]>();
			nestedRelation.add(relation);
			relation = TurWEMRelator.nestedRelators(turingTag.getSrcAttributeRelation(), nestedRelation, 0);
		}

		if (relation.length > 0) {
			widgetName = relation[0].getAttribute(attributeName).getAttributeDefinition().getWidgetName();
		}
		return widgetName;
	}
}
