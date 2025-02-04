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

import com.viglet.turing.wem.broker.indexer.TurWEMIndexer;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.as.server.event.AsDeploymentEvent;
import com.vignette.logging.context.ContextLogger;

public class DeploymentHandler {

	private static final ContextLogger log = ContextLogger.getLogger(DeploymentHandler.class.getName());

    private IHandlerConfiguration config;

    public DeploymentHandler(IHandlerConfiguration config) {
        this.config = config;
    }

    public void onManagedObjectCreate(ManagedObject mo, AsDeploymentEvent deploymentEvent) {
        String siteName = deploymentEvent.getSiteName();
        boolean result = TurWEMIndexer.indexCreate(mo, config, siteName);
        log.debug("Viglet Turing Indexing Create: " + result);
     }

    public void onManagedObjectUpdate(ManagedObject mo, AsDeploymentEvent deploymentEvent) {
        String siteName = deploymentEvent.getSiteName();
        boolean result = TurWEMIndexer.indexUpdate(mo, config, siteName);
        log.debug("Viglet Turing Indexing Update: " + result);
    }

    public void onManagedObjectDelete(ManagedObject mo, AsDeploymentEvent deploymentEvent) {
        String siteName = deploymentEvent.getSiteName();
        boolean result = TurWEMIndexer.indexDelete(mo, config, siteName);
        log.debug("Viglet Turing Indexing Delete: " + result);
    }

}