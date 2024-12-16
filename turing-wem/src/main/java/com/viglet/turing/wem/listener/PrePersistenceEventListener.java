/*
 * Copyright (C) 2016-2021 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
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

import java.lang.invoke.MethodHandles;

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

	private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());

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
