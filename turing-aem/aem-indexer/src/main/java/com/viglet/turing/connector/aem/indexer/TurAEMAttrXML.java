package com.viglet.turing.connector.aem.indexer;

import com.viglet.turing.connector.cms.beans.TurAttrDef;
import com.viglet.turing.connector.cms.beans.TurAttrDefContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Property;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

public class TurAEMAttrXML {
	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private TurAEMAttrXML() {
		throw new IllegalStateException("TurAEMAttrXML");
	}

	public static List<TurAttrDef> attributeXML(TurAttrDefContext turAttrDefContext) throws Exception {
		TuringTag turingTag = turAttrDefContext.getTuringTag();
		if (turingTag.getTextValue() != null && !turingTag.getTextValue().isEmpty()) {
			List<TurAttrDef> attributesDefs = new ArrayList<>();
			TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(),
					TurMultiValue.singleItem(turingTag.getTextValue()));
			attributesDefs.add(turAttrDef);
			return attributesDefs;
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("attributeXML getSrcXmlName(): %s", turingTag.getSrcXmlName()));
			}
			return addAttribute(turAttrDefContext);
		}
	}

	public static List<TurAttrDef> attributeXMLUpdate(TurAttrDefContext turAttrDefContext, Property jcrProperty)
			throws Exception {
		TuringTag turingTag = turAttrDefContext.getTuringTag();
		if (logger.isDebugEnabled() && jcrProperty != null)
			logger.debug(String.format("%s = %s", turingTag.getTagName(), AemObject.getPropertyValue(jcrProperty)));

		if (hasJcrPropertyValue(jcrProperty))
			return TurAEMAttrClass.attributeByClass(turAttrDefContext, jcrProperty);

		return new ArrayList<>();
	}

	private static List<TurAttrDef> addAttribute(TurAttrDefContext turAttrDefContext) throws Exception {
		TuringTag turingTag = turAttrDefContext.getTuringTag();
		AemObject aemObject = (AemObject) turAttrDefContext.getCMSObjectInstance();
		String attributeName = turAttrDefContext.getTuringTag().getSrcXmlName();
		Property jcrProperty = null;
		if (attributeName != null) {
			if (aemObject.getJcrContentNode().hasProperty(attributeName)) {
				jcrProperty = aemObject.getJcrContentNode().getProperty(attributeName);
			}
			else if (aemObject.getAttributes().containsKey(attributeName)){
				jcrProperty = aemObject.getAttributes().get(attributeName);
			}
		}
		if (hasJcrPropertyValue(jcrProperty)) {
			return attributeXMLUpdate(turAttrDefContext, jcrProperty);
		} else if (turingTag.getSrcClassName() != null) {
			return TurAEMAttrClass.attributeByClass(turAttrDefContext, jcrProperty);
		} else {
			return new ArrayList<>();
		}
	}

	private static boolean hasJcrPropertyValue(Property jcrProperty) {
		return jcrProperty != null && AemObject.getPropertyValue(jcrProperty) != null;
	}

}
