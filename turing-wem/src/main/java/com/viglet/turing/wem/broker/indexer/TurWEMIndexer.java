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
