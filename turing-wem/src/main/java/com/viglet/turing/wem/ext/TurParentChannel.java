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
package com.viglet.turing.wem.ext;

import java.lang.invoke.MethodHandles;

import com.viglet.turing.wem.beans.TurMultiValue;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.util.ETLTuringTranslator;
import com.viglet.turing.wem.util.TuringUtils;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.common.ref.ChannelRef;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

public class TurParentChannel implements ExtAttributeInterface {
	private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());

	public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData,
			IHandlerConfiguration config) throws Exception {
		log.debug("Executing TurParentChannel");
		ChannelRef[] channelRefs = ci.getChannelAssociations();
		if (channelRefs.length > 0) {
			ETLTuringTranslator etlTranslator = new ETLTuringTranslator(config);
			return TurMultiValue.singleItem(etlTranslator.translateByGUID(TuringUtils
					.getParentChannelFromBreadcrumb(channelRefs[0].getChannel().getBreadcrumbPath(true)).getId()));
		}
		return new TurMultiValue();
	}
}
