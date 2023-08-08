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
package com.viglet.turing.wem.listener;

import java.lang.invoke.MethodHandles;

import com.viglet.turing.wem.broker.indexer.TurWEMIndexer;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.server.event.AsPrePersistenceEvent;
import com.vignette.logging.context.ContextLogger;

public class PrePersistenceHandler {

	private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());

	private IHandlerConfiguration config;

	public PrePersistenceHandler(IHandlerConfiguration config) {
		this.config = config;
	}

	public void onPrePersistenceDelete(AsPrePersistenceEvent prePersistenceEvent) {
		boolean result = TurWEMIndexer.indexDelete(prePersistenceEvent, config);
		log.debug("Viglet Turing Indexing Delete: " + result);
	}

}