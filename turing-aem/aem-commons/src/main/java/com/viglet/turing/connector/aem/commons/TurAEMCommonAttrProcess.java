package com.viglet.turing.connector.aem.commons;

import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.connector.cms.beans.TurCmsContext;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValue;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueList;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import com.viglet.turing.connector.cms.util.HtmlManipulator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class TurAEMCommonAttrProcess {
    public static final String JCR_TITLE = "jcr:title";
    public static final String CQ_TAGS = "cq:tags";
    public static final String TAG_SEPARATOR = ":";
    public static final String DEFAULT = "default";
    public static final String TEXT = "text";
    public static final String JCR = "jcr:";
    public static final String JSON = ".json";
    public static final String SLING = "sling:";

    public static boolean hasClassNoSource(TurCmsTargetAttr targetAttr) {
        return targetAttr.getSourceAttrs() == null
                && StringUtils.isNotBlank(targetAttr.getClassName());
    }

    public static boolean hasTextValue(TurCmsTargetAttr turCmsTargetAttr) {
        return StringUtils.isNotEmpty(turCmsTargetAttr.getTextValue());
    }

    @Nullable
    public static Object getJcrProperty(TurCmsContext context, String sourceAttrName) {
        return Optional.ofNullable(sourceAttrName).map(attrName -> {
            AemObject aemObject = (AemObject) context.getCmsObjectInstance();
            if (aemObject.getJcrContentNode() != null && aemObject.getJcrContentNode().has(attrName)) {
                return aemObject.getJcrContentNode().get(attrName);
            } else if (aemObject.getAttributes().containsKey(attrName))
                return aemObject.getAttributes().get(attrName);
            return null;
        }).orElse(null);

    }

    @NotNull
    public static TurSNAttributeSpec getTurSNAttributeSpec(String facet, Map<String, String> facetLabel) {
        TurSNAttributeSpec turSNAttributeSpec = new TurSNAttributeSpec();
        turSNAttributeSpec.setName(facet);
        turSNAttributeSpec.setDescription(facetLabel.get(DEFAULT));
        turSNAttributeSpec.setFacetName(facetLabel);
        turSNAttributeSpec.setFacet(true);
        turSNAttributeSpec.setMandatory(false);
        turSNAttributeSpec.setType(TurSEFieldType.STRING);
        turSNAttributeSpec.setMultiValued(true);
        return turSNAttributeSpec;
    }

    public static Map<String, String> getTagLabels(JSONObject tagJson) {
        Map<String, String> labels = new HashMap<>();
        if (tagJson.has(JCR_TITLE))
            labels.put(DEFAULT, tagJson.getString(JCR_TITLE));
        Iterator<String> keys = tagJson.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String titleStartWith = JCR_TITLE + ".";
            if (key.startsWith(titleStartWith)) {
                String locale = key.replaceAll(titleStartWith, "");
                if (locale.equals("pt_br"))
                    locale = "pt_BR";
                else if (locale.equals("en_us"))
                    locale = "en_US";
                labels.put(locale, tagJson.getString(key));
            }
        }
        return labels;
    }

    public static TurCmsTargetAttrValueList addValuesToAttributes(TurCmsTargetAttr turCmsTargetAttr,
                                                                  TurCmsSourceAttr turCmsSourceAttr,
                                                                  Object jcrProperty) {

        if (turCmsSourceAttr.isConvertHtmlToText()) {
            return TurCmsTargetAttrValueList.singleItem(turCmsTargetAttr.getName(),
                    HtmlManipulator.html2Text(TurAEMCommonsUtils.getPropertyValue(jcrProperty)));
        } else if (jcrProperty != null) {
            TurMultiValue turMultiValue = new TurMultiValue();
            if (isJSONArray(jcrProperty)) {
                ((JSONArray) jcrProperty).forEach(item -> turMultiValue.add(item.toString()));
            } else {
                turMultiValue.add(TurAEMCommonsUtils.getPropertyValue(jcrProperty));
            }
            if (!turMultiValue.isEmpty()) {
                return TurCmsTargetAttrValueList.singleItem(turCmsTargetAttr.getName(), turMultiValue);
            }
        }
        return new TurCmsTargetAttrValueList();
    }

    private static boolean isJSONArray(Object jcrProperty) {
        return (jcrProperty instanceof JSONArray)
                && !((JSONArray) jcrProperty).isEmpty();
    }

    public static boolean hasCustomClass(TurCmsContext context) {
        return StringUtils.isNotBlank(context.getTurCmsSourceAttr().getClassName());
    }

    public static boolean hasJcrPropertyValue(Object jcrProperty) {
        return ObjectUtils.allNotNull(jcrProperty, TurAEMCommonsUtils.getPropertyValue(jcrProperty));
    }

    public static TurCmsTargetAttrValue getTurAttrDefUnique(TurCmsTargetAttr turCmsTargetAttr,
                                                            TurCmsTargetAttrValueList turCmsTargetAttrValueList) {
        TurMultiValue multiValue = new TurMultiValue();
        turCmsTargetAttrValueList.stream()
                .flatMap(targetAttrValue -> targetAttrValue.getMultiValue().stream())
                .distinct().forEach(multiValue::add);
        return new TurCmsTargetAttrValue(turCmsTargetAttr.getName(), multiValue);
    }
}
