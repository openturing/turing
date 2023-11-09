package com.viglet.turing.connector.aem.indexer;

import com.viglet.turing.connector.cms.beans.TurAttrDef;
import com.viglet.turing.connector.cms.beans.TurAttrDefContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class TurAEMAttrXML {
    private TurAEMAttrXML() {
        throw new IllegalStateException("TurAEMAttrXML");
    }

    public static List<TurAttrDef> attributeXML(TurAttrDefContext turAttrDefContext) {
        TuringTag turingTag = turAttrDefContext.getTuringTag();
        if (turingTag.getTextValue() != null && !turingTag.getTextValue().isEmpty()) {
            List<TurAttrDef> attributesDefs = new ArrayList<>();
            TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(),
                    TurMultiValue.singleItem(turingTag.getTextValue()));
            attributesDefs.add(turAttrDef);
            return attributesDefs;
        } else {
            if (log.isDebugEnabled()) {
                log.debug(String.format("attributeXML getSrcXmlName(): %s", turingTag.getSrcXmlName()));
            }
            return addAttribute(turAttrDefContext);
        }
    }

    public static List<TurAttrDef> attributeXMLUpdate(TurAttrDefContext turAttrDefContext, Object jcrProperty)
            throws Exception {
        TuringTag turingTag = turAttrDefContext.getTuringTag();
        if (log.isDebugEnabled() && jcrProperty != null)
            log.debug(String.format("%s = %s", turingTag.getTagName(), TurAemUtils.getPropertyValue(jcrProperty)));

        if (hasJcrPropertyValue(jcrProperty))
            return TurAEMAttrClass.attributeByClass(turAttrDefContext, jcrProperty);

        return new ArrayList<>();
    }

    private static List<TurAttrDef> addAttribute(TurAttrDefContext turAttrDefContext) {
        TuringTag turingTag = turAttrDefContext.getTuringTag();
        AemObject aemObject = (AemObject) turAttrDefContext.getCMSObjectInstance();
        String attributeName = turAttrDefContext.getTuringTag().getSrcXmlName();
        Object jcrProperty = null;
        if (attributeName != null) {
            if (aemObject.getJcrContentNode().has(attributeName)) {
                jcrProperty = aemObject.getJcrContentNode().get(attributeName);
            } else if (aemObject.getAttributes().containsKey(attributeName)) {
                jcrProperty = aemObject.getAttributes().get(attributeName);
            }
        }
        if (hasJcrPropertyValue(jcrProperty)) {
            try {
                return attributeXMLUpdate(turAttrDefContext, jcrProperty);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        } else if (turingTag.getSrcClassName() != null) {
            try {
                return TurAEMAttrClass.attributeByClass(turAttrDefContext, jcrProperty);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return new ArrayList<>();
    }

    private static boolean hasJcrPropertyValue(Object jcrProperty) {
        return jcrProperty != null && TurAemUtils.getPropertyValue(jcrProperty) != null;
    }

}
