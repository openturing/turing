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

import java.util.HashMap;
import java.util.Map;

import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.config.TurSNSiteConfig;
import com.viglet.turing.wem.util.TuringUtils;
import com.vignette.as.client.common.AsLocaleData;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;

public class TurWEMDeindex {

	private TurWEMDeindex() {
		throw new IllegalStateException("TurWEMDeindex");
	}

	// This method deletes the content to the Viglet Turing broker
	public static void indexDelete(ManagedObjectVCMRef managedObjectVCMRef, IHandlerConfiguration config) {

		final TurSNJobItems turSNJobItems = new TurSNJobItems();
		final TurSNJobItem turSNJobItem = new TurSNJobItem();
		String siteName = TuringUtils.getSiteNameFromManagedObjectVCMRef(managedObjectVCMRef, config);
		AsLocaleData asLocaleData = TuringUtils.getAsLocaleDataFromManagedObject(managedObjectVCMRef);
		TurSNSiteConfig turSNSiteConfig = config.getSNSiteConfig(siteName, asLocaleData);
		turSNJobItem.setTurSNJobAction(TurSNJobAction.DELETE);
		turSNJobItem.setLocale(turSNSiteConfig.getLocale());
		Map<String, Object> attributes = new HashMap<>();

		String guid = managedObjectVCMRef.getId();
		attributes.put(IHandlerConfiguration.ID_ATTRIBUTE, guid);
		attributes.put(IHandlerConfiguration.PROVIDER_ATTRIBUTE, config.getProviderName());
		turSNJobItem.setAttributes(attributes);
		turSNJobItems.add(turSNJobItem);

		TuringUtils.sendToTuring(turSNJobItems, config, turSNSiteConfig);
	}

	public static void indexDeleteByType(String siteName, String typeName, IHandlerConfiguration config) {
		final TurSNJobItems turSNJobItems = new TurSNJobItems();
		final TurSNJobItem turSNJobItem = new TurSNJobItem();
		TurSNSiteConfig turSNSiteConfig = config.getSNSiteConfig(siteName);
		turSNJobItem.setTurSNJobAction(TurSNJobAction.DELETE);
		turSNJobItem.setLocale(turSNSiteConfig.getLocale());
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(IHandlerConfiguration.TYPE_ATTRIBUTE, typeName);
		attributes.put(IHandlerConfiguration.PROVIDER_ATTRIBUTE, config.getProviderName());
		turSNJobItem.setAttributes(attributes);
		turSNJobItems.add(turSNJobItem);
		TuringUtils.sendToTuring(turSNJobItems, config, turSNSiteConfig);
	}
}
