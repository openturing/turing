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
import com.viglet.turing.wem.broker.relator.TurWEMRelator;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.javabean.AttributedObject;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

public class TurWEMAttrXML {
	private static final ContextLogger log = ContextLogger.getLogger(TurWEMAttrXML.class);

	private TurWEMAttrXML() {
		throw new IllegalStateException("TurWEMAttrXML");
	}

	public static List<TurAttrDef> attributeXML(TurAttrDefContext turAttrDefContext) throws Exception {
		TuringTag turingTag = turAttrDefContext.getTuringTag();
		if (turingTag.getTextValue() != null && !turingTag.getTextValue().isEmpty()) {
			List<TurAttrDef> attributesDefs = new ArrayList<TurAttrDef>();
			TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(),
					TurMultiValue.singleItem(turingTag.getTextValue()));
			attributesDefs.add(turAttrDef);
			return attributesDefs;
		} else {
			if (turingTag.getSrcAttributeRelation() != null && !turingTag.getSrcAttributeRelation().isEmpty())
				return addAttributeWithRelator(turAttrDefContext);
			else
				return addAttributeWithoutRelator(turAttrDefContext);
		}
	}

	public static List<TurAttrDef> attributeXMLUpdate(TurAttrDefContext turAttrDefContext, AttributeData attributeData)
			throws Exception {
		TuringTag turingTag = turAttrDefContext.getTuringTag();
		if (log.isDebugEnabled() && attributeData != null)
			log.debug(String.format("%s = %s", turingTag.getTagName(), attributeData.getValue().toString()));

		if (attributeData != null && attributeData.getValue().toString() != null
				&& attributeData.getValue().toString().trim().length() > 0)
			return TurWEMAttrWidget.attributeByWidget(turAttrDefContext, attributeData);

		return new ArrayList<TurAttrDef>();
	}

	private static List<TurAttrDef> addAttributeWithRelator(TurAttrDefContext turAttrDefContext) throws Exception {
		TuringTag turingTag = turAttrDefContext.getTuringTag();
		ContentInstance ci = turAttrDefContext.getContentInstance();
		String attributeName = turAttrDefContext.getTuringTag().getSrcXmlName();
		AttributedObject[] relation = ci.getRelations(turingTag.getSrcAttributeRelation().get(0));
		List<TurAttrDef> attributesDefs = new ArrayList<TurAttrDef>();

		relation = getRelationIfExists(turingTag, relation);
		addRelationAttributes(turAttrDefContext, attributeName, relation, attributesDefs);

		return attributesDefs;
	}

	private static AttributedObject[] getRelationIfExists(TuringTag turingTag, AttributedObject[] relation) {
		if (turingTag.getSrcAttributeRelation().size() > 1) {
			log.debug("Attribute has nested relator");
			List<AttributedObject[]> nestedRelation = new ArrayList<AttributedObject[]>();
			nestedRelation.add(relation);
			relation = TurWEMRelator.nestedRelators(turingTag.getSrcAttributeRelation(), nestedRelation, 0);
		}
		return relation;
	}

	private static void addRelationAttributes(TurAttrDefContext turAttrDefContext, String attributeName,
			AttributedObject[] relation, List<TurAttrDef> attributesDefs) throws Exception {
		if (relation != null) {
			for (AttributedObject attributedObject : relation) {
				if (attributedObject.getAttributeValue(attributeName) != null) {
					String attributeValue = attributedObject.getAttributeValue(attributeName).toString();
					AttributeData attributeData = attributedObject.getAttribute(attributeName);
					if (log.isDebugEnabled())
						log.debug(String.format("Attribute: %s,  Value: %s", attributeName, attributeValue));
					if (attributeValue != null && attributeValue.trim().length() > 0)
						attributesDefs.addAll(attributeXMLUpdate(turAttrDefContext, attributeData));
				}
			}
		}
	}

	private static List<TurAttrDef> addAttributeWithoutRelator(TurAttrDefContext turAttrDefContext) throws Exception {
		TuringTag turingTag = turAttrDefContext.getTuringTag();
		ContentInstance ci = turAttrDefContext.getContentInstance();
		String attributeName = turAttrDefContext.getTuringTag().getSrcXmlName();
		if (ci.getAttributeValue(attributeName) != null
				&& ci.getAttributeValue(attributeName).toString().trim().length() > 0) {
			AttributeData attributeData = ci.getAttribute(attributeName);
			return attributeXMLUpdate(turAttrDefContext, attributeData);
		} else if (turingTag.getSrcClassName() != null) {
			AttributeData attributeData = ci.getAttribute(attributeName);
			return TurWEMAttrClass.attributeByClass(turAttrDefContext, attributeData);
		} else {
			return new ArrayList<TurAttrDef>();
		}
	}

}
