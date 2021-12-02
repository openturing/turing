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

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.util.TuringUtils;
import com.vignette.as.client.common.AsLocaleData;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.javabean.ManagedObject;

import com.vignette.logging.context.ContextLogger;

public class TurWEMDeindex {
	private static final ContextLogger logger = ContextLogger.getLogger(TurWEMDeindex.class);

	private TurWEMDeindex() {
		throw new IllegalStateException("TurWEMDeindex");
	}

	// This method deletes the content to the Viglet Turing broker
	public static void indexDelete(ManagedObjectVCMRef managedObjectVCMRef, IHandlerConfiguration config) {

		final TurSNJobItems turSNJobItems = new TurSNJobItems();
		final TurSNJobItem turSNJobItem = new TurSNJobItem();
		String siteName = null;
		try {
			siteName = TuringUtils.getSiteName(managedObjectVCMRef.retrieveManagedObject(), config);
		} catch (ApplicationException | RemoteException e) {
			logger.error(e.getMessage(), e);
		}
		turSNJobItem.setTurSNJobAction(TurSNJobAction.DELETE);
		turSNJobItem.setLocale("en_US");
		Map<String, Object> attributes = new HashMap<>();

		String guid = managedObjectVCMRef.getId();
		attributes.put("id", guid);
		attributes.put("provider", "WEM");
		turSNJobItem.setAttributes(attributes);
		turSNJobItems.add(turSNJobItem);

		try {
			ManagedObject mo = managedObjectVCMRef.retrieveManagedObject();
			AsLocaleData asLocaleData = null;
			if (mo != null && mo.getLocale() != null && mo.getLocale().getAsLocale() != null
					&& mo.getLocale().getAsLocale().getData() != null)
				asLocaleData = mo.getLocale().getAsLocale().getData();
			TuringUtils.sendToTuring(turSNJobItems, config, siteName, asLocaleData);
		} catch (IOException | ApplicationException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public static void indexDeleteByType(String siteName, String typeName, IHandlerConfiguration config) {
		final TurSNJobItems turSNJobItems = new TurSNJobItems();
		final TurSNJobItem turSNJobItem = new TurSNJobItem();
		
		turSNJobItem.setTurSNJobAction(TurSNJobAction.DELETE);
		turSNJobItem.setLocale("en_US");
		Map<String, Object> attributes = new HashMap<>();
		attributes.put("type", typeName);
		attributes.put("provider", "WEM");
		turSNJobItem.setAttributes(attributes);
		turSNJobItems.add(turSNJobItem);
		try {
			AsLocaleData asLocaleData = null;
			TuringUtils.sendToTuring(turSNJobItems, config, siteName, asLocaleData);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
	}
}
