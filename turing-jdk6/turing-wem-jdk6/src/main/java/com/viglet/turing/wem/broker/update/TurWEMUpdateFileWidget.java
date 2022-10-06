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


import java.util.ArrayList;
import java.util.List;

import com.viglet.turing.wem.beans.TurAttrDef;
import com.viglet.turing.wem.beans.TurAttrDefContext;
import com.viglet.turing.wem.beans.TurMultiValue;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.util.TuringUtils;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.javabean.ContentInstance;

import com.vignette.logging.context.ContextLogger;

public class TurWEMUpdateFileWidget {
	private static final ContextLogger log = ContextLogger.getLogger(TurWEMUpdateFileWidget.class.getName());

	private TurWEMUpdateFileWidget() {
		throw new IllegalStateException("TurWEMUpdateFileWidget");
	}
	
	public static List<TurAttrDef> attributeFileWidgetUpdate(TurAttrDefContext turAttrDefContext,
			AttributeData attributeData) throws Exception {

		TuringTag turingTag = turAttrDefContext.getTuringTag();
		ContentInstance ci = turAttrDefContext.getContentInstance();
		IHandlerConfiguration config = turAttrDefContext.getiHandlerConfiguration();

		if (log.isDebugEnabled()) {
			log.debug("TurWEMUpdateFileWidget started");
		}

		List<TurAttrDef> attributesDefs = new ArrayList<TurAttrDef>();

		if (turingTag.getSrcClassName() == null) {

			String url = TuringUtils.getSiteDomain(ci, config) + attributeData.getValue().toString();
			if (log.isDebugEnabled())
				log.debug("TurWEMUpdateFileWidget url" + url);

			TurMultiValue turMultiValue = new TurMultiValue();
			turMultiValue.add(url);
			TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
			attributesDefs.add(turAttrDef);

		}

		if (log.isDebugEnabled())
			log.debug("TurWEMUpdateFileWidget finished");

		return attributesDefs;

	}
}
