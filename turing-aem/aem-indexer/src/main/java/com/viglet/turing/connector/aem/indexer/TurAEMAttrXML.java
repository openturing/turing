package com.viglet.turing.connector.aem.indexer;

import com.viglet.turing.connector.aem.indexer.ext.ExtAttributeInterface;
import com.viglet.turing.connector.cms.beans.TurAttrDef;
import com.viglet.turing.connector.cms.beans.TurAttrDefContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.util.HtmlManipulator;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Slf4j
public class TurAEMAttrXML {
    private TurAEMAttrXML() {
        throw new IllegalStateException("TurAEMAttrXML");
    }

    public static List<TurAttrDef> attributeXML(TurAttrDefContext turAttrDefContext) {
        TuringTag turingTag = turAttrDefContext.getTuringTag();
        if (log.isDebugEnabled()) {
            log.debug(String.format("attributeXML getSrcXmlName(): %s", turingTag.getSrcXmlName()));
        }
        if (isTextValue(turingTag)) {
            return setLiteralTextValueToAttribute(turingTag);
        } else {
            return hasCustomClass(turAttrDefContext)?
                    attributeByClass(turAttrDefContext):
                    attributeByCMS(turAttrDefContext);
        }
    }

    private static boolean isTextValue(TuringTag turingTag) {
        return turingTag.getTextValue() != null && !turingTag.getTextValue().isEmpty();
    }

    private static List<TurAttrDef> setLiteralTextValueToAttribute(TuringTag turingTag) {
        List<TurAttrDef> attributesDefs = new ArrayList<>();
        TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(),
                TurMultiValue.singleItem(turingTag.getTextValue()));
        attributesDefs.add(turAttrDef);
        return attributesDefs;
    }

    private static List<TurAttrDef> attributeByCMS(TurAttrDefContext turAttrDefContext) {
        String attributeName = turAttrDefContext.getTuringTag().getSrcXmlName();
        Object jcrProperty = null;
        if (attributeName != null) {
            AemObject aemObject = (AemObject) turAttrDefContext.getCMSObjectInstance();
            if (aemObject.getJcrContentNode().has(attributeName))
                jcrProperty = aemObject.getJcrContentNode().get(attributeName);
            else if (aemObject.getAttributes().containsKey(attributeName))
                jcrProperty = aemObject.getAttributes().get(attributeName);
        }
        if (hasJcrPropertyValue(jcrProperty)) {
            return addValuesToAttributes(turAttrDefContext, jcrProperty);
        }
        return Collections.emptyList();
    }

    private static List<TurAttrDef> addValuesToAttributes(TurAttrDefContext turAttrDefContext, Object jcrProperty) {
        TuringTag turingTag = turAttrDefContext.getTuringTag();
        List<TurAttrDef> attributesDefs = new ArrayList<>();
        TurMultiValue turMultiValue = new TurMultiValue();
        if (isHtmlAttribute(turingTag)) {
            turMultiValue.add(HtmlManipulator.html2Text(TurAemUtils.getPropertyValue(jcrProperty)));
            attributesDefs.add(new TurAttrDef(turingTag.getTagName(), turMultiValue));
        } else if (jcrProperty != null) {
            if (isJSONArray(jcrProperty)) {
                ((JSONArray) jcrProperty).forEach(item -> turMultiValue.add(item.toString()));
            } else {
                turMultiValue.add(TurAemUtils.getPropertyValue(jcrProperty));
            }
            if (!turMultiValue.isEmpty()) {
                TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
                attributesDefs.add(turAttrDef);
            }
        }
        return attributesDefs;
    }

    public static List<TurAttrDef> attributeByClass(TurAttrDefContext turAttrDefContext) {
        TuringTag turingTag = turAttrDefContext.getTuringTag();
        IHandlerConfiguration config = turAttrDefContext.getiHandlerConfiguration();
        List<TurAttrDef> attributesDefs = new ArrayList<>();
        String className = turingTag.getSrcClassName();
        if (log.isDebugEnabled())
            log.debug("ClassName : " + className);

        Object extAttribute = null;
        try {
            extAttribute = Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException | ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        TurMultiValue turMultiValue = ((ExtAttributeInterface) Objects.requireNonNull(extAttribute)).consume(turingTag,
                (AemObject) turAttrDefContext.getCMSObjectInstance(), config);
        TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
        attributesDefs.add(turAttrDef);
        return attributesDefs;
    }

    private static boolean isJSONArray(Object jcrProperty) {
        return (jcrProperty instanceof JSONArray)
                && !((JSONArray) jcrProperty).isEmpty();
    }

    private static boolean isHtmlAttribute(TuringTag turingTag) {
        return turingTag.getSrcAttributeType() != null && turingTag.getSrcAttributeType().equals("html");
    }

    private static boolean hasCustomClass(TurAttrDefContext turAttrDefContext) {
        return turAttrDefContext.getTuringTag().getSrcClassName() != null;
    }

    private static boolean hasJcrPropertyValue(Object jcrProperty) {
        return jcrProperty != null && TurAemUtils.getPropertyValue(jcrProperty) != null;
    }
}
