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

import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.util.TuringUtils;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.as.server.event.AsPrePersistenceEvent;
import com.vignette.logging.context.ContextLogger;

import java.lang.invoke.MethodHandles;

public class TurWEMIndexer {

	private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());
	public static final String DELETE_MESSAGE = "Deleting Object in Viglet Turing index";
	public static final String UPDATE_MESSAGE = "Updating Object in Viglet Turing index";
	public static final String CREATE_MESSAGE = "Creating Object in Viglet Turing index";

	private TurWEMIndexer() {
		throw new IllegalStateException("TurWEMIndexer");
	}

	public static boolean indexCreate(ManagedObject mo, IHandlerConfiguration config, String siteName) {
		indexerLog(CREATE_MESSAGE);
		TurWEMIndex.indexCreate(mo, config, siteName);
		return true;
	}

	public static boolean indexUpdate(ManagedObject mo, IHandlerConfiguration config, String siteName) {
		indexerLog(UPDATE_MESSAGE);
		TurWEMIndex.indexCreate(mo, config, siteName);
		return true;
	}

	public static boolean indexDelete(AsPrePersistenceEvent prePersistenceEvent,
			IHandlerConfiguration config) {
		indexerLog(DELETE_MESSAGE);
		TurWEMDeindex.indexDelete(prePersistenceEvent.getManagedObject().getContentManagementId() , config,
				TuringUtils.getSiteNameFromContentInstance(prePersistenceEvent.getManagedObject(), config));
		return true;
	}

	private static void indexerLog(String message) {
		if (log.isDebugEnabled()) {
			log.debug(message);
		}
	}

	public static boolean indexDelete(ManagedObject mo,
									  IHandlerConfiguration config, String siteName) {
		indexerLog(DELETE_MESSAGE);
		TurWEMDeindex.indexDelete(mo.getContentManagementId() , config, siteName);
		return true;
	}

	public static void indexDeleteByType(String siteName, String typeName, IHandlerConfiguration config) {
		indexerLog(DELETE_MESSAGE);
		TurWEMDeindex.indexDeleteByType(siteName, typeName, config);
	}
}
