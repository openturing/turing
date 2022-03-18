/*
 * Copyright (C) 2016-2022 the original author or authors. 
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

import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.wem.config.GenericResourceHandlerConfiguration;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.config.TurSNSiteConfig;
import com.viglet.turing.wem.util.TuringUtils;
import com.vignette.as.client.common.AsLocaleData;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.logging.context.ContextLogger;

import java.util.HashMap;
import java.util.Map;

public class TurWEMDeindex {

	private TurWEMDeindex() {
		throw new IllegalStateException(TurWEMDeindex.class.getName());
	}

	private static final ContextLogger log = ContextLogger.getLogger(TurWEMDeindex.class);

	/**
	 * Create and send a job to delete a spotlight or deindex a content in Turing
	 * Semantic Navigation Site.
	 * 
	 **/
	public static void indexDelete(ManagedObjectVCMRef managedObjectVCMRef, IHandlerConfiguration config,
			String siteName) {
		final TurSNJobItems turSNJobItems = new TurSNJobItems();
		final TurSNJobItem turSNJobItem = new TurSNJobItem();

		AsLocaleData asLocaleData = null;
		String ctdXmlName = null;
		try {
			ManagedObject mo = managedObjectVCMRef.retrieveManagedObject();
			if (mo != null) {
				if ((mo.getLocale() != null) && (mo.getLocale().getAsLocale() != null)
						&& (mo.getLocale().getAsLocale().getData() != null))
					asLocaleData = mo.getLocale().getAsLocale().getData();
				ctdXmlName = mo.getObjectType().getData().getName();
			}

		} catch (Exception e) {
			log.error("Error while retrieving locale from MO. Id " + managedObjectVCMRef.getId(), e);
		}

		TurSNSiteConfig turSNSiteConfig = config.getSNSiteConfig(siteName, asLocaleData);

		turSNJobItem.setTurSNJobAction(TurSNJobAction.DELETE);
		turSNJobItem.setLocale(turSNSiteConfig.getLocale());
		Map<String, Object> attributes = new HashMap<>();

		String guid = managedObjectVCMRef.getId();
		attributes.put(GenericResourceHandlerConfiguration.ID_ATTRIBUTE, guid);
		attributes.put(GenericResourceHandlerConfiguration.PROVIDER_ATTRIBUTE, config.getProviderName());
		attributes.put(GenericResourceHandlerConfiguration.TYPE_ATTRIBUTE, ctdXmlName);

		turSNJobItem.setAttributes(attributes);
		turSNJobItems.add(turSNJobItem);

		TuringUtils.sendToTuring(turSNJobItems, config, turSNSiteConfig);
	}

	/**
	 * Create and send a job to delete a spotlight or deindex a context by CTD XML
	 * Name in Turing Semantic Navigation Site.
	 * 
	 **/
	public static void indexDeleteByType(String siteName, String typeName, IHandlerConfiguration config) {
		final TurSNJobItems turSNJobItems = new TurSNJobItems();
		final TurSNJobItem turSNJobItem = new TurSNJobItem();
		TurSNSiteConfig turSNSiteConfig = config.getSNSiteConfig(siteName);
		turSNJobItem.setTurSNJobAction(TurSNJobAction.DELETE);
		turSNJobItem.setLocale(turSNSiteConfig.getLocale());
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(GenericResourceHandlerConfiguration.TYPE_ATTRIBUTE, typeName);
		attributes.put(GenericResourceHandlerConfiguration.PROVIDER_ATTRIBUTE, config.getProviderName());
		turSNJobItem.setAttributes(attributes);
		turSNJobItems.add(turSNJobItem);
		TuringUtils.sendToTuring(turSNJobItems, config, turSNSiteConfig);
	}
}
