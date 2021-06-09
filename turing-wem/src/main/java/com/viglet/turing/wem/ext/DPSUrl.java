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
import com.viglet.turing.wem.util.ETLTuringTranslator;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

public class DPSUrl implements ExtAttributeInterface {
	private static final ContextLogger log = ContextLogger.getLogger(DPSUrl.class);

	@Override
	public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData,
			IHandlerConfiguration config) throws Exception {
		ETLTuringTranslator etlTranslator = new ETLTuringTranslator(config);

		if (log.isDebugEnabled()) {
			log.debug("Executing DPSUrl");
		}

		String attribContent = null;
		if (attributeData != null) {
			attribContent = attributeData.getValue().toString();
		}
		TurMultiValue turMultiValue = new TurMultiValue();

		if (attribContent == null) {
			turMultiValue.add(etlTranslator.translateByGUID(ci.getContentManagementId().getId()));
			return turMultiValue;
		} else {
			if (attribContent.toLowerCase().startsWith("http"))
				turMultiValue.add(attribContent);
			else
				turMultiValue.add(etlTranslator.translate(attribContent));
		}
		return turMultiValue;
	}

}
