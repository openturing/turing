/*
 * Copyright (C) 2016-2019 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.wem.listener;

import com.viglet.turing.wem.broker.indexer.TurWEMIndexer;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.as.server.event.AsDeploymentEvent;
import com.vignette.logging.context.ContextLogger;


public class DeploymentHandler {

	private static final ContextLogger log = ContextLogger.getLogger(DeploymentHandler.class);

    IHandlerConfiguration config;

    public DeploymentHandler(IHandlerConfiguration config) {
        this.config = config;
    }

    public void onManagedObjectCreate(ManagedObject mo, AsDeploymentEvent deploymentEvent) {
        boolean result = TurWEMIndexer.indexCreate(mo, config);
        log.debug("Viglet Turing Indexing Create: " + result);
     }

    public void onManagedObjectUpdate(ManagedObject mo, AsDeploymentEvent deploymentEvent) {
        boolean result = TurWEMIndexer.indexUpdate(mo, config);
        log.debug("Viglet Turing Indexing Update: " + result);
    }

    public void onManagedObjectDelete(ManagedObject mo, AsDeploymentEvent deploymentEvent) {
       
        boolean result = TurWEMIndexer.indexDelete(mo, config);
        log.debug("Viglet Turing Indexing Delete: " + result);
    }

}