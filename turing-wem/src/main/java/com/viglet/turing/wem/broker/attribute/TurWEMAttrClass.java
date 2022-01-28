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
package com.viglet.turing.wem.broker.attribute;

import java.util.ArrayList;
import java.util.List;

import com.viglet.turing.wem.beans.TurAttrDef;
import com.viglet.turing.wem.beans.TurAttrDefContext;
import com.viglet.turing.wem.beans.TurMultiValue;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.ext.ExtAttributeInterface;
import com.viglet.turing.wem.util.HtmlManipulator;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

public class TurWEMAttrClass {

	private static final ContextLogger log = ContextLogger.getLogger(TurWEMAttrClass.class);

	private TurWEMAttrClass() {
		throw new IllegalStateException("TurWEMAttrClass");
	}

	public static List<TurAttrDef> attributeByClass(TurAttrDefContext turAttrDefContext, AttributeData attributeData)
			throws Exception {

		TuringTag turingTag = turAttrDefContext.getTuringTag();
		ContentInstance ci = turAttrDefContext.getContentInstance();
		IHandlerConfiguration config = turAttrDefContext.getiHandlerConfiguration();
		List<TurAttrDef> attributesDefs = new ArrayList<>();

		if (turingTag.getSrcClassName() != null) {
			String className = turingTag.getSrcClassName();
			if (log.isDebugEnabled())
				log.debug("ClassName : " + className);

			Object extAttribute = Class.forName(className).getDeclaredConstructor().newInstance();
			TurMultiValue turMultiValue = ((ExtAttributeInterface) extAttribute).consume(turingTag, ci, attributeData,
					config);
			TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
			attributesDefs.add(turAttrDef);
		} else {
			TurMultiValue turMultiValue = new TurMultiValue();
			if (turingTag.getSrcAttributeType() != null && turingTag.getSrcAttributeType().equals("html")) {
				turMultiValue.add(HtmlManipulator.html2Text(attributeData.getValue().toString()));
				TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
				attributesDefs.add(turAttrDef);
			} else if (attributeData != null && attributeData.getValue() != null) {
				turMultiValue.add(attributeData.getValue().toString());
				TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
				attributesDefs.add(turAttrDef);
			}

		}
		return attributesDefs;
	}
}
