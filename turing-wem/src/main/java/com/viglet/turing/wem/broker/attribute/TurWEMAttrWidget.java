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
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.broker.relator.TurWEMRelator;
import com.viglet.turing.wem.broker.update.TurWEMUpdateContentSelectWidget;
import com.viglet.turing.wem.broker.update.TurWEMUpdateFileWidget;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.javabean.AttributedObject;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

public class TurWEMAttrWidget {
	private static final ContextLogger log = ContextLogger.getLogger(TurWEMAttrWidget.class);

	private TurWEMAttrWidget() {
		throw new IllegalStateException("TurWEMAttrWidget");
	}
	
	public static List<TurAttrDef> attributeByWidget(TurAttrDefContext turAttrDefContext, AttributeData attributeData)
			throws Exception {
		TuringTag turingTag = turAttrDefContext.getTuringTag();
		ContentInstance ci = turAttrDefContext.getContentInstance();
		String attributeName = turAttrDefContext.getTuringTag().getSrcXmlName();
		String widgetName = null;

		if (turingTag.getSrcAttributeRelation() != null && !turingTag.getSrcAttributeRelation().isEmpty()) {
			widgetName = relationWidgetName(turingTag, ci, attributeName, widgetName);

		} else {
			widgetName = ci.getAttribute(attributeName).getAttributeDefinition().getWidgetName();
		}

		if (widgetName != null && widgetName.equals("WCMContentSelectWidget")
				&& attributeData.getValue().toString().length() == 40 && turingTag.getSrcClassName() == null) {
			if (log.isDebugEnabled()) {
				log.debug("WCMContentSelectWidget value: " + attributeData.getValue().toString());
				log.debug("WCMContentSelectWidget length: " + attributeData.getValue().toString().length());
			}
			return TurWEMUpdateContentSelectWidget.attributeContentSelectUpdate(turAttrDefContext,
					attributeData);

		} else if (widgetName != null && widgetName.equals("VCMFileWidget") && turingTag.getSrcClassName() == null) {
			if (log.isDebugEnabled()) {
				log.debug("VCMFileWidget value: " + attributeData.getValue().toString());
				log.debug("VCMFileWidget length: " + attributeData.getValue().toString().length());
			}
			return TurWEMUpdateFileWidget.attributeFileWidgetUpdate(turAttrDefContext,
					attributeData);
		} else {
			return TurWEMAttrClass.attributeByClass(turAttrDefContext,
					attributeData);
		}

	}

	private static String relationWidgetName(TuringTag turingTag, ContentInstance ci, String attributeName,
			String widgetName) throws ApplicationException {
		AttributedObject[] relation = ci.getRelations(turingTag.getSrcAttributeRelation().get(0));

		if (turingTag.getSrcAttributeRelation().size() > 1) {
			List<AttributedObject[]> nestedRelation = new ArrayList<AttributedObject[]>();
			nestedRelation.add(relation);
			relation = TurWEMRelator.nestedRelators(turingTag.getSrcAttributeRelation(), nestedRelation, 0);
		}

		if (relation.length > 0) {
			widgetName = relation[0].getAttribute(attributeName).getAttributeDefinition().getWidgetName();
		}
		return widgetName;
	}
}
