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

import com.viglet.turing.wem.config.GenericResourceHandlerConfiguration;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.exception.AuthorizationException;
import com.vignette.as.client.exception.ValidationException;
import com.vignette.as.server.event.AsEvent;
import com.vignette.as.server.event.AsPrePersistenceEvent;
import com.vignette.as.server.event.IAsEventListener;
import com.vignette.logging.context.ContextLogger;

public class PrePersistenceEventListener implements IAsEventListener {

	private static final ContextLogger log = ContextLogger.getLogger(PrePersistenceEventListener.class.getName());

	public void consume(AsEvent event) throws ApplicationException, AuthorizationException, ValidationException {
		try {
			// If this is an actual item being deployed, process the item
			if (event instanceof AsPrePersistenceEvent) {
				AsPrePersistenceEvent prePersistenceEvent = (AsPrePersistenceEvent) event;
				String type = prePersistenceEvent.getType();

				IHandlerConfiguration config = new GenericResourceHandlerConfiguration();

				PrePersistenceHandler handler = new PrePersistenceHandler(config);

				// Call out to the appropriate method depending on event type
				if (type.equals(AsPrePersistenceEvent.PRE_DELETE)) {
					log.debug("Viglet Turing PrePersistenceEvent() - Delete object");
					handler.onPrePersistenceDelete(prePersistenceEvent);
				}
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	public int getPriority() {
		return HIGH_PRIORITY;
	}

}
