package com.viglet.turing.connector.aem.commons;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.commons.cache.TurCustomClassCache;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.TurAemExtAttributeInterface;
import com.viglet.turing.connector.cms.beans.TurCmsContext;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueMap;
import com.viglet.turing.connector.cms.mappers.TurCmsContentDefinitionProcess;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

@Slf4j
public class TurAemAttrProcess {
    public static final String JCR_TITLE = "jcr:title";
    public static final String CQ_TAGS = "cq:tags";
    public static final String TAG_SEPARATOR = ":";
    public static final String DEFAULT = "default";
    public static final String TEXT = "text";
    public static final String JCR = "jcr:";
    public static final String JSON = ".json";
    public static final String SLING = "sling:";

    public static boolean hasCustomClass(TurCmsTargetAttr targetAttr) {
        return targetAttr.getSourceAttrs() == null
                && StringUtils.isNotBlank(targetAttr.getClassName());
    }

    public static boolean hasTextValue(TurCmsTargetAttr turCmsTargetAttr) {
        return StringUtils.isNotEmpty(turCmsTargetAttr.getTextValue());
    }

    @Nullable
    public static Object getJcrProperty(TurCmsContext context, String sourceAttrName) {
        return Optional.ofNullable(sourceAttrName).map(attrName -> {
            TurAemObject aemObject = (TurAemObject) context.getCmsObjectInstance();
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

    public static TurCmsTargetAttrValueMap addValuesToAttributes(TurCmsTargetAttr turCmsTargetAttr,
                                                                 TurCmsSourceAttr turCmsSourceAttr,
                                                                 Object jcrProperty) {

        if (turCmsSourceAttr.isConvertHtmlToText()) {
            return TurCmsTargetAttrValueMap.singleItem(turCmsTargetAttr.getName(),
                    TurCommonsUtils.html2Text(TurAemCommonsUtils.getPropertyValue(jcrProperty)), false);
        } else if (jcrProperty != null) {
            TurMultiValue turMultiValue = new TurMultiValue();
            if (isJSONArray(jcrProperty)) {
                ((JSONArray) jcrProperty).forEach(item -> turMultiValue.add(item.toString()));
            } else {
                turMultiValue.add(TurAemCommonsUtils.getPropertyValue(jcrProperty));
            }
            if (!turMultiValue.isEmpty()) {
                return TurCmsTargetAttrValueMap.singleItem(turCmsTargetAttr.getName(), turMultiValue, false);
            }
        }
        return new TurCmsTargetAttrValueMap();
    }

    private static boolean isJSONArray(Object jcrProperty) {
        return jcrProperty instanceof JSONArray jsonArray
                && !jsonArray.isEmpty();
    }

    public static boolean hasCustomClass(TurCmsContext context) {
        return StringUtils.isNotBlank(context.getTurCmsSourceAttr().getClassName());
    }

    public static boolean hasJcrPropertyValue(Object jcrProperty) {
        return ObjectUtils.allNotNull(jcrProperty, TurAemCommonsUtils.getPropertyValue(jcrProperty));
    }

    public static TurCmsTargetAttrValueMap getTurAttrDefUnique(TurCmsTargetAttr turCmsTargetAttr,
                                                               TurCmsTargetAttrValueMap turCmsTargetAttrValueMap) {
        return TurCmsTargetAttrValueMap.singleItem(turCmsTargetAttr.getName(),
                turCmsTargetAttrValueMap.get(turCmsTargetAttr.getName()).stream().distinct().toList(), false);
    }

    public static TurSNAttributeSpec setTagFacet(TurAemSourceContext turAemSourceContext,
                                                 String facetId) {
        return TurAemCommonsUtils
                .getInfinityJson("/content/_cq_tags/%s".formatted(facetId),
                        turAemSourceContext, true).map(infinityJson ->
                        getTurSNAttributeSpec(facetId, getTagLabels(infinityJson))).orElse(new TurSNAttributeSpec());
    }

    public static String addTagToAttrValueList(TurCmsContext context, TurAemSourceContext turAemSourceContext,
                                               String facet, String value) {
        return TurAemCommonsUtils
                .getInfinityJson("/content/_cq_tags/%s/%s".formatted(facet, value),
                        turAemSourceContext, true).map(infinityJson -> {
                    Locale locale = TurAemCommonsUtils.getLocaleFromContext(turAemSourceContext, context);
                    String titleLocale = locale.toString().toLowerCase();
                    String titleLanguage = locale.getLanguage().toLowerCase();
                    Map<String, String> tagLabels = getTagLabels(infinityJson);
                    if (tagLabels.containsKey(titleLocale))
                        return tagLabels.get(titleLocale);
                    else if (tagLabels.containsKey(titleLanguage))
                        return tagLabels.get(titleLanguage);
                    else return tagLabels.getOrDefault(DEFAULT, value);
                }).orElse(value);

    }

    public TurCmsTargetAttrValueMap prepareAttributeDefs(TurAemObject aemObject,
                                                         TurCmsContentDefinitionProcess turCmsContentDefinitionProcess,
                                                         List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                         TurAemSourceContext turAemSourceContext) {
        return turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(aemObject.getType())
                .map(turCmsModel -> {
                    TurCmsContext context = new TurCmsContext(aemObject, turCmsContentDefinitionProcess.getConfig());
                    TurCmsTargetAttrValueMap turCmsTargetAttrValueMap = new TurCmsTargetAttrValueMap();
                    turCmsModel.getTargetAttrs().stream().filter(Objects::nonNull)
                            .forEach(targetAttr -> {
                                log.debug("TargetAttr: {}", targetAttr);
                                context.setTurCmsTargetAttr(targetAttr);
                                if (hasCustomClass(targetAttr)) {
                                    turCmsTargetAttrValueMap.merge(process(context, turSNAttributeSpecList,
                                            turAemSourceContext));
                                } else {
                                    targetAttr.getSourceAttrs().stream().filter(Objects::nonNull)
                                            .forEach(sourceAttr ->
                                                    turCmsTargetAttrValueMap.merge(
                                                            addTargetAttrValuesBySourceAttr(turAemSourceContext,
                                                                    turSNAttributeSpecList,
                                                                    targetAttr, sourceAttr, context)));
                                }
                            });
                    return turCmsTargetAttrValueMap;
                }).orElseGet(() -> {
                    log.error("Content Type not found: {}", aemObject.getType());
                    return new TurCmsTargetAttrValueMap();
                });
    }

    public TurCmsTargetAttrValueMap addTargetAttrValuesBySourceAttr(TurAemSourceContext turAemSourceContext,
                                                                    List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                                    TurCmsTargetAttr targetAttr,
                                                                    TurCmsSourceAttr sourceAttr,
                                                                    TurCmsContext context) {

        context.setTurCmsSourceAttr(sourceAttr);
        TurCmsTargetAttrValueMap targetAttrValues = process(context, turSNAttributeSpecList, turAemSourceContext);
        return sourceAttr.isUniqueValues() ? getTurAttrDefUnique(targetAttr, targetAttrValues) : targetAttrValues;
    }

    public TurCmsTargetAttrValueMap process(TurCmsContext context,
                                            List<TurSNAttributeSpec> turSNAttributeSpecList,
                                            TurAemSourceContext turAemSourceContext) {
        log.debug("Target Attribute Name: {} and Source Attribute Name: {}",
                context.getTurCmsTargetAttr().getName(), context.getTurCmsSourceAttr().getName());
        if (hasTextValue(context.getTurCmsTargetAttr())) {
            return getTextValue(context);
        } else {
            return getCustomClassValue(context, turSNAttributeSpecList, turAemSourceContext);
        }
    }

    private @NotNull TurCmsTargetAttrValueMap getCustomClassValue(TurCmsContext context,
                                                                  List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                                  TurAemSourceContext turAemSourceContext) {
        TurCmsTargetAttrValueMap turCmsTargetAttrValueMap = hasCustomClass(context) ?
                attributeByClass(context, turAemSourceContext) :
                attributeByCMS(context);
        turCmsTargetAttrValueMap.merge(generateNewAttributesFromCqTags(context,
                turAemSourceContext, turSNAttributeSpecList));
        return turCmsTargetAttrValueMap;
    }

    private static @NotNull TurCmsTargetAttrValueMap getTextValue(TurCmsContext context) {
        return TurCmsTargetAttrValueMap.singleItem(context.getTurCmsTargetAttr(), false);
    }

    private TurCmsTargetAttrValueMap attributeByCMS(TurCmsContext context) {
        String sourceAttrName = context.getTurCmsSourceAttr().getName();
        final Object jcrProperty = getJcrProperty(context, sourceAttrName);
        return hasJcrPropertyValue(jcrProperty) ?
                addValuesToAttributes(context.getTurCmsTargetAttr(),
                        context.getTurCmsSourceAttr(), jcrProperty) :
                new TurCmsTargetAttrValueMap();
    }

    private TurCmsTargetAttrValueMap generateNewAttributesFromCqTags(TurCmsContext context,
                                                                     TurAemSourceContext turAemSourceContext,
                                                                     List<TurSNAttributeSpec> turSNAttributeSpecList) {
        TurCmsTargetAttrValueMap turCmsTargetAttrValueMap = new TurCmsTargetAttrValueMap();
        String attributeName = context.getTurCmsSourceAttr().getName();
        Object jcrProperty = getJcrProperty(context, attributeName);
        if (CQ_TAGS.equals(attributeName)) {
            Optional.ofNullable((JSONArray) jcrProperty).ifPresent(property ->
                    property.forEach(tag -> {
                        String[] tagSplit = tag.toString().split(TAG_SEPARATOR);
                        if (tagSplit.length >= 2) {
                            Optional.ofNullable(tagSplit[0]).ifPresent(facet -> {
                                turSNAttributeSpecList.add(setTagFacet(turAemSourceContext, facet));
                                Optional.ofNullable(tagSplit[1]).ifPresent(value ->
                                        turCmsTargetAttrValueMap.addWithSingleValue(facet,
                                                addTagToAttrValueList(context, turAemSourceContext, facet, value), false)
                                );
                            });
                        }
                    })
            );
        }
        return turCmsTargetAttrValueMap;
    }

    private TurCmsTargetAttrValueMap attributeByClass(TurCmsContext context, TurAemSourceContext turAemSourceContext) {
        String className = context.getTurCmsSourceAttr().getClassName();
        log.debug("ClassName : {}", className);
        return TurCustomClassCache.getCustomClassMap(className)
                .map(classInstance -> TurCmsTargetAttrValueMap.singleItem(context
                                .getTurCmsTargetAttr().getName(),
                        ((TurAemExtAttributeInterface) classInstance)
                                .consume(context.getTurCmsTargetAttr(),
                                        context.getTurCmsSourceAttr(),
                                        (TurAemObject) context.getCmsObjectInstance(),
                                        turAemSourceContext),
                        false))
                .orElseGet(TurCmsTargetAttrValueMap::new);

    }

}
