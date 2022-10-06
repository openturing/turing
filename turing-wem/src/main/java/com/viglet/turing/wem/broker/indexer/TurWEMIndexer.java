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
package com.viglet.turing.wem.broker.indexer;

import java.lang.invoke.MethodHandles;

import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.util.TuringUtils;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.as.server.event.AsPrePersistenceEvent;
import com.vignette.logging.context.ContextLogger;

public class TurWEMIndexer {

	private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());

	private TurWEMIndexer() {
		throw new IllegalStateException("TurWEMIndexer");
	}

	public static boolean indexCreate(ManagedObject mo, IHandlerConfiguration config, String siteName) {
		if (log.isDebugEnabled()) {
			log.debug("Creating Object in Viglet Turing index");
		}
		TurWEMIndex.indexCreate(mo, config, siteName);
		return true;
	}

	public static boolean indexUpdate(ManagedObject mo, IHandlerConfiguration config, String siteName) {
		if (log.isDebugEnabled()) {
			log.debug("Updating Object in Viglet Turing index");
		}
		TurWEMIndex.indexCreate(mo, config, siteName);
		return true;
	}

	public static boolean indexDelete(AsPrePersistenceEvent prePersistenceEvent,
			IHandlerConfiguration config) {
		if (log.isDebugEnabled()) {
			log.debug("Deleting Object in Viglet Turing index");
		}
		String siteName = TuringUtils.getSiteNameFromContentInstance(prePersistenceEvent.getManagedObject(), config);
		TurWEMDeindex.indexDelete(prePersistenceEvent.getManagedObject().getContentManagementId() , config, siteName);
		return true;
	}

	public static boolean indexDelete(ManagedObject mo,
									  IHandlerConfiguration config, String siteName) {
		if (log.isDebugEnabled()) {
			log.debug("Deleting Object in Viglet Turing index");
		}
		TurWEMDeindex.indexDelete(mo.getContentManagementId() , config, siteName);
		return true;
	}

	public static boolean indexDeleteByType(String siteName, String typeName, IHandlerConfiguration config) {
		if (log.isDebugEnabled()) {
			log.debug("Deleting Object in Viglet Turing index");
		}
		TurWEMDeindex.indexDeleteByType(siteName, typeName, config);
		return true;
	}
}
