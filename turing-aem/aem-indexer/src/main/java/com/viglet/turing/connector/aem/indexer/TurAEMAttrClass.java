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
package com.viglet.turing.connector.aem.indexer;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import javax.jcr.Property;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viglet.turing.connector.aem.indexer.ext.ExtAttributeInterface;
import com.viglet.turing.connector.cms.beans.TurAttrDef;
import com.viglet.turing.connector.cms.beans.TurAttrDefContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.util.HtmlManipulator;

public class TurAEMAttrClass {
	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private TurAEMAttrClass() {
		throw new IllegalStateException("TurAEMAttrClass");
	}

	public static List<TurAttrDef> attributeByClass(TurAttrDefContext turAttrDefContext, Property jcrProperty)
			throws Exception {

		TuringTag turingTag = turAttrDefContext.getTuringTag();
		IHandlerConfiguration config = turAttrDefContext.getiHandlerConfiguration();
		List<TurAttrDef> attributesDefs = new ArrayList<>();

		if (turingTag.getSrcClassName() != null) {
			String className = turingTag.getSrcClassName();
			if (logger.isDebugEnabled())
				logger.debug("ClassName : " + className);

			Object extAttribute = Class.forName(className).getDeclaredConstructor().newInstance();
			TurMultiValue turMultiValue = ((ExtAttributeInterface) extAttribute).consume(turingTag,
					(AemObject) turAttrDefContext.getCMSObjectInstance(), config);
			TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
			attributesDefs.add(turAttrDef);
		} else {
			TurMultiValue turMultiValue = new TurMultiValue();
			if (turingTag.getSrcAttributeType() != null && turingTag.getSrcAttributeType().equals("html")) {
				turMultiValue.add(HtmlManipulator.html2Text( AemObject.getPropertyValue(jcrProperty)));
				TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
				attributesDefs.add(turAttrDef);
			} else if (jcrProperty != null && jcrProperty.getValue() != null) {
				turMultiValue.add(AemObject.getPropertyValue(jcrProperty));
				TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
				attributesDefs.add(turAttrDef);
			}

		}
		return attributesDefs;
	}
}
