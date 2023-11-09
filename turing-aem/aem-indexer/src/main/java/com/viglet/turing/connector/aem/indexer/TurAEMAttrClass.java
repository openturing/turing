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

import com.viglet.turing.connector.aem.indexer.ext.ExtAttributeInterface;
import com.viglet.turing.connector.cms.beans.TurAttrDef;
import com.viglet.turing.connector.cms.beans.TurAttrDefContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.util.HtmlManipulator;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
public class TurAEMAttrClass {
	private TurAEMAttrClass() {
		throw new IllegalStateException("TurAEMAttrClass");
	}

	public static List<TurAttrDef> attributeByClass(TurAttrDefContext turAttrDefContext, Object jcrProperty)
			throws Exception {

		TuringTag turingTag = turAttrDefContext.getTuringTag();
		IHandlerConfiguration config = turAttrDefContext.getiHandlerConfiguration();
		List<TurAttrDef> attributesDefs = new ArrayList<>();

		if (turingTag.getSrcClassName() != null) {
			String className = turingTag.getSrcClassName();
			if (log.isDebugEnabled())
				log.debug("ClassName : " + className);

			Object extAttribute = Class.forName(className).getDeclaredConstructor().newInstance();
			TurMultiValue turMultiValue = ((ExtAttributeInterface) extAttribute).consume(turingTag,
					(AemObject) turAttrDefContext.getCMSObjectInstance(), config);
			TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
			attributesDefs.add(turAttrDef);
		} else {
			TurMultiValue turMultiValue = new TurMultiValue();
			if (turingTag.getSrcAttributeType() != null && turingTag.getSrcAttributeType().equals("html")) {
				turMultiValue.add(HtmlManipulator.html2Text( TurAemUtils.getPropertyValue(jcrProperty)));
				TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
				attributesDefs.add(turAttrDef);
			} else if (jcrProperty != null) {
				if (( jcrProperty instanceof JSONArray)
						&& !((JSONArray)jcrProperty).isEmpty()) {
					((JSONArray)jcrProperty).forEach(item -> {
						turMultiValue.add(item.toString());
					});
				}
				else {
					turMultiValue.add(TurAemUtils.getPropertyValue(jcrProperty));

				}
				if (!turMultiValue.isEmpty()) {
					TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
					attributesDefs.add(turAttrDef);
				}
			}

		}
		return attributesDefs;
	}
}
