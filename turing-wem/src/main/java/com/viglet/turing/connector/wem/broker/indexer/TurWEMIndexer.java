/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.connector.wem.broker.indexer;

import com.viglet.turing.connector.wem.config.IHandlerConfiguration;
import com.viglet.turing.connector.wem.util.TuringUtils;
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
