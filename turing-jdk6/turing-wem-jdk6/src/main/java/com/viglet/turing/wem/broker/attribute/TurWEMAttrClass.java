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
import com.viglet.turing.wem.beans.TurMultiValue;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.ext.ExtAttributeInterface;
import com.viglet.turing.wem.util.HtmlManipulator;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

public class TurWEMAttrClass {

	private static final ContextLogger log = ContextLogger.getLogger(TurWEMAttrClass.class.getName());

	private TurWEMAttrClass() {
		throw new IllegalStateException("TurWEMAttrClass");
	}

	public static List<TurAttrDef> attributeByClass(TurAttrDefContext turAttrDefContext, AttributeData attributeData)
			throws Exception {

		TuringTag turingTag = turAttrDefContext.getTuringTag();
		ContentInstance ci = turAttrDefContext.getContentInstance();
		IHandlerConfiguration config = turAttrDefContext.getiHandlerConfiguration();
		List<TurAttrDef> attributesDefs = new ArrayList<TurAttrDef>();

		if (turingTag.getSrcClassName() != null) {
			String className = turingTag.getSrcClassName();
			if (log.isDebugEnabled())
				log.debug("ClassName : " + className);

			Object extAttribute = Class.forName(className).getDeclaredConstructor().newInstance();
			TurMultiValue turMultiValue = ((ExtAttributeInterface) extAttribute).consume(turingTag, ci, attributeData,
					config);
			TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
			attributesDefs.add(turAttrDef);
		} else {
			TurMultiValue turMultiValue = new TurMultiValue();
			if (turingTag.getSrcAttributeType() != null && turingTag.getSrcAttributeType().equals("html")) {
				turMultiValue.add(HtmlManipulator.html2Text(attributeData.getValue().toString()));
				TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
				attributesDefs.add(turAttrDef);
			} else if (attributeData != null && attributeData.getValue() != null) {
				turMultiValue.add(attributeData.getValue().toString());
				TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
				attributesDefs.add(turAttrDef);
			}

		}
		return attributesDefs;
	}
}
