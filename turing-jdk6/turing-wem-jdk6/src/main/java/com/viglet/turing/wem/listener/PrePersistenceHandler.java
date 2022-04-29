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
import com.vignette.as.server.event.AsPrePersistenceEvent;
import com.vignette.logging.context.ContextLogger;

public class PrePersistenceHandler {

	private static final ContextLogger log = ContextLogger.getLogger(PrePersistenceHandler.class.getName());

	private IHandlerConfiguration config;

	public PrePersistenceHandler(IHandlerConfiguration config) {
		this.config = config;
	}

	public void onPrePersistenceDelete(AsPrePersistenceEvent prePersistenceEvent) {
		boolean result = TurWEMIndexer.indexDelete(prePersistenceEvent, config);
		log.debug("Viglet Turing Indexing Delete: " + result);
	}

}