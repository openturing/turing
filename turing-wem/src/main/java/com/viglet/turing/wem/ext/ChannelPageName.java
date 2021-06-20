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
package com.viglet.turing.wem.ext;

import com.viglet.turing.wem.beans.TurMultiValue;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.javabean.Channel;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.logging.context.ContextLogger;

public class ChannelPageName implements ExtAttributeInterface {
	private static final ContextLogger log = ContextLogger.getLogger(ChannelPageName.class);

	@Override
	public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData, IHandlerConfiguration config)
			throws Exception {
		String name = "";
		if (log.isDebugEnabled()) {
			log.debug("Executing ChannelPageName");
		}
		for (ManagedObjectVCMRef mo : ManagedObject.getReferringManagedObjects(ci.getContentManagementId())) {
			if (mo.getObjectTypeRef().getObjectType().getName().equals("Channel")) {
				Channel channel = (Channel) mo.asManagedObjectRef().retrieveManagedObject();
				name = channel.getName();

			}

		}
		TurMultiValue turMultiValue = new TurMultiValue();
		turMultiValue.add(name);
		
		return turMultiValue;
	}	
}
