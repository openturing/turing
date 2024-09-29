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
package com.viglet.turing.connector.wem.broker.update;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

import com.viglet.turing.connector.wem.beans.TurAttrDef;
import com.viglet.turing.connector.wem.beans.TurAttrDefContext;
import com.viglet.turing.connector.wem.beans.TurMultiValue;
import com.viglet.turing.connector.wem.beans.TuringTag;
import com.viglet.turing.connector.wem.config.IHandlerConfiguration;
import com.viglet.turing.connector.wem.util.TuringUtils;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.javabean.ContentInstance;

import com.vignette.logging.context.ContextLogger;

public class TurWEMUpdateFileWidget {
	private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());

	private TurWEMUpdateFileWidget() {
		throw new IllegalStateException("TurWEMUpdateFileWidget");
	}
	
	public static List<TurAttrDef> attributeFileWidgetUpdate(TurAttrDefContext turAttrDefContext,
			AttributeData attributeData) {

		TuringTag turingTag = turAttrDefContext.getTuringTag();
		ContentInstance ci = turAttrDefContext.getContentInstance();
		IHandlerConfiguration config = turAttrDefContext.getiHandlerConfiguration();

		if (log.isDebugEnabled()) {
			log.debug("TurWEMUpdateFileWidget started");
		}

		List<TurAttrDef> attributesDefs = new ArrayList<>();

		if (turingTag.getSrcClassName() == null) {

			String url = TuringUtils.getSiteDomain(ci, config) + attributeData.getValue().toString();
			if (log.isDebugEnabled())
				log.debug("TurWEMUpdateFileWidget url" + url);

			TurMultiValue turMultiValue = new TurMultiValue();
			turMultiValue.add(url);
			TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
			attributesDefs.add(turAttrDef);

		}

		if (log.isDebugEnabled())
			log.debug("TurWEMUpdateFileWidget finished");

		return attributesDefs;

	}
}
