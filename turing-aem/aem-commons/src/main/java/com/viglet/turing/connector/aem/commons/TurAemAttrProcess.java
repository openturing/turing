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

package com.viglet.turing.connector.aem.commons;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.commons.cache.TurCustomClassCache;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.aem.commons.bean.TurAemContext;
import com.viglet.turing.connector.aem.commons.bean.TurAemTargetAttrValueMap;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.TurAemExtAttributeInterface;
import com.viglet.turing.connector.aem.commons.mappers.TurAemContentDefinitionProcess;
import com.viglet.turing.connector.aem.commons.mappers.TurAemTargetAttr;
import com.viglet.turing.connector.aem.commons.mappers.TurAemSourceAttr;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;

import static com.viglet.turing.connector.aem.commons.TurAemConstants.*;

@Slf4j
public class TurAemAttrProcess {


    public static boolean hasCustomClass(TurAemTargetAttr targetAttr) {
        return targetAttr.getSourceAttrs() == null
                && StringUtils.isNotBlank(targetAttr.getClassName());
    }

    public static boolean hasTextValue(TurAemTargetAttr turAemTargetAttr) {
        return StringUtils.isNotEmpty(turAemTargetAttr.getTextValue());
    }

    @Nullable
    public static Object getJcrProperty(TurAemContext context, String sourceAttrName) {
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

    public static TurAemTargetAttrValueMap addValuesToAttributes(TurAemTargetAttr turAemTargetAttr,
                                                                 TurAemSourceAttr turAemSourceAttr,
                                                                 Object jcrProperty) {

        if (turAemSourceAttr.isConvertHtmlToText()) {
            return TurAemTargetAttrValueMap.singleItem(turAemTargetAttr.getName(),
                    TurCommonsUtils.html2Text(TurAemCommonsUtils.getPropertyValue(jcrProperty)), false);
        } else if (jcrProperty != null) {
            TurMultiValue turMultiValue = new TurMultiValue();
            if (isJSONArray(jcrProperty)) {
                ((JSONArray) jcrProperty).forEach(item -> turMultiValue.add(item.toString()));
            } else {
                turMultiValue.add(TurAemCommonsUtils.getPropertyValue(jcrProperty));
            }
            if (!turMultiValue.isEmpty()) {
                return TurAemTargetAttrValueMap.singleItem(turAemTargetAttr.getName(), turMultiValue, false);
            }
        }
        return new TurAemTargetAttrValueMap();
    }

    private static boolean isJSONArray(Object jcrProperty) {
        return jcrProperty instanceof JSONArray jsonArray
                && !jsonArray.isEmpty();
    }

    public static boolean hasCustomClass(TurAemContext context) {
        return StringUtils.isNotBlank(context.getTurAemSourceAttr().getClassName());
    }

    public static boolean hasJcrPropertyValue(Object jcrProperty) {
        return ObjectUtils.allNotNull(jcrProperty, TurAemCommonsUtils.getPropertyValue(jcrProperty));
    }

    public static TurAemTargetAttrValueMap getTurAttrDefUnique(TurAemTargetAttr turAemTargetAttr,
                                                               TurAemTargetAttrValueMap turAemTargetAttrValueMap) {
        return TurAemTargetAttrValueMap.singleItem(turAemTargetAttr.getName(),
                turAemTargetAttrValueMap.get(turAemTargetAttr.getName()).stream().distinct().toList(), false);
    }

    public static TurSNAttributeSpec setTagFacet(TurAemSourceContext turAemSourceContext,
                                                 String facetId) {
        return TurAemCommonsUtils
                .getInfinityJson("/content/_cq_tags/%s".formatted(facetId),
                        turAemSourceContext, true).map(infinityJson ->
                        getTurSNAttributeSpec(facetId, getTagLabels(infinityJson))).orElse(new TurSNAttributeSpec());
    }

    public static String addTagToAttrValueList(TurAemContext context, TurAemSourceContext turAemSourceContext,
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

    public TurAemTargetAttrValueMap prepareAttributeDefs(TurAemObject aemObject,
                                                         TurAemContentDefinitionProcess turAemContentDefinitionProcess,
                                                         List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                         TurAemSourceContext turAemSourceContext) {
        return turAemContentDefinitionProcess.findByNameFromModelWithDefinition(aemObject.getType())
                .map(turAemModel -> {
                    TurAemContext context = new TurAemContext(aemObject, turAemContentDefinitionProcess.getConfig());
                    TurAemTargetAttrValueMap turAemTargetAttrValueMap = new TurAemTargetAttrValueMap();
                    turAemModel.getTargetAttrs().stream().filter(Objects::nonNull)
                            .forEach(targetAttr -> {
                                log.debug("TargetAttr: {}", targetAttr);
                                context.setTurAemTargetAttr(targetAttr);
                                if (hasCustomClass(targetAttr)) {
                                    turAemTargetAttrValueMap.merge(process(context, turSNAttributeSpecList,
                                            turAemSourceContext));
                                } else {
                                    targetAttr.getSourceAttrs().stream().filter(Objects::nonNull)
                                            .forEach(sourceAttr ->
                                                    turAemTargetAttrValueMap.merge(
                                                            addTargetAttrValuesBySourceAttr(turAemSourceContext,
                                                                    turSNAttributeSpecList,
                                                                    targetAttr, sourceAttr, context)));
                                }
                            });
                    return turAemTargetAttrValueMap;
                }).orElseGet(() -> {
                    log.error("Content Type not found: {}", aemObject.getType());
                    return new TurAemTargetAttrValueMap();
                });
    }

    public TurAemTargetAttrValueMap addTargetAttrValuesBySourceAttr(TurAemSourceContext turAemSourceContext,
                                                                    List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                                    TurAemTargetAttr targetAttr,
                                                                    TurAemSourceAttr sourceAttr,
                                                                    TurAemContext context) {

        context.setTurAemSourceAttr(sourceAttr);
        TurAemTargetAttrValueMap targetAttrValues = process(context, turSNAttributeSpecList, turAemSourceContext);
        return sourceAttr.isUniqueValues() ? getTurAttrDefUnique(targetAttr, targetAttrValues) : targetAttrValues;
    }

    public TurAemTargetAttrValueMap process(TurAemContext context,
                                            List<TurSNAttributeSpec> turSNAttributeSpecList,
                                            TurAemSourceContext turAemSourceContext) {
        log.debug("Target Attribute Name: {} and Source Attribute Name: {}",
                context.getTurAemTargetAttr().getName(), context.getTurAemSourceAttr().getName());
        if (hasTextValue(context.getTurAemTargetAttr())) {
            return getTextValue(context);
        } else {
            return getCustomClassValue(context, turSNAttributeSpecList, turAemSourceContext);
        }
    }

    private @NotNull TurAemTargetAttrValueMap getCustomClassValue(TurAemContext context,
                                                                  List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                                  TurAemSourceContext turAemSourceContext) {
        TurAemTargetAttrValueMap turAemTargetAttrValueMap = hasCustomClass(context) ?
                attributeByClass(context, turAemSourceContext) :
                attributeByCMS(context);
        turAemTargetAttrValueMap.merge(generateNewAttributesFromCqTags(context,
                turAemSourceContext, turSNAttributeSpecList, turAemTargetAttrValueMap));
        return turAemTargetAttrValueMap;
    }

    private static @NotNull TurAemTargetAttrValueMap getTextValue(TurAemContext context) {
        return TurAemTargetAttrValueMap.singleItem(context.getTurAemTargetAttr(), false);
    }

    private TurAemTargetAttrValueMap attributeByCMS(TurAemContext context) {
        String sourceAttrName = context.getTurAemSourceAttr().getName();
        final Object jcrProperty = getJcrProperty(context, sourceAttrName);
        return hasJcrPropertyValue(jcrProperty) ?
                addValuesToAttributes(context.getTurAemTargetAttr(),
                        context.getTurAemSourceAttr(), jcrProperty) :
                new TurAemTargetAttrValueMap();
    }

    private TurAemTargetAttrValueMap generateNewAttributesFromCqTags(TurAemContext context,
                                                                     TurAemSourceContext turAemSourceContext,
                                                                     List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                                     TurAemTargetAttrValueMap turAemTargetAttrValueMapFromClass) {
        TurAemTargetAttrValueMap turAemTargetAttrValueMap = new TurAemTargetAttrValueMap();
        String attributeName = context.getTurAemSourceAttr().getName();
        if (CQ_TAGS.equals(attributeName)) {
            String targetName = context.getTurAemTargetAttr().getName();
            if (turAemTargetAttrValueMapFromClass.containsKey(targetName)) {
                processTagsFromTargetAttr(context, turAemSourceContext, turSNAttributeSpecList,
                        turAemTargetAttrValueMapFromClass, targetName, turAemTargetAttrValueMap);
            } else {
                processTagsFromSourceAttr(context, turAemSourceContext, turSNAttributeSpecList, attributeName,
                        turAemTargetAttrValueMap);
            }
        }
        return turAemTargetAttrValueMap;
    }

    private static void processTagsFromSourceAttr(TurAemContext context, TurAemSourceContext turAemSourceContext,
                                                  List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                  String attributeName,
                                                  TurAemTargetAttrValueMap turAemTargetAttrValueMap) {
        Optional.ofNullable((JSONArray) getJcrProperty(context, attributeName))
                .ifPresent(property ->
                        property.forEach(tag ->
                                formatTags(context, turAemSourceContext, turSNAttributeSpecList,
                                        tag.toString(), turAemTargetAttrValueMap))
                );
    }

    private static void processTagsFromTargetAttr(TurAemContext context, TurAemSourceContext turAemSourceContext,
                                                  List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                  TurAemTargetAttrValueMap turAemTargetAttrValueMapFromClass,
                                                  String targetName,
                                                  TurAemTargetAttrValueMap turAemTargetAttrValueMap) {
        turAemTargetAttrValueMapFromClass.get(targetName).forEach(tag ->
                formatTags(context, turAemSourceContext, turSNAttributeSpecList,
                        tag, turAemTargetAttrValueMap));
    }

    private static void formatTags(TurAemContext context, TurAemSourceContext turAemSourceContext,
                                   List<TurSNAttributeSpec> turSNAttributeSpecList, String tag,
                                   TurAemTargetAttrValueMap turAemTargetAttrValueMap) {
        TurCommonsUtils.getKeyValueFromColon(tag).ifPresent(kv ->
                Optional.ofNullable(kv.getKey()).ifPresent(facet -> {
                    turSNAttributeSpecList.add(setTagFacet(turAemSourceContext, facet));
                    Optional.ofNullable(kv.getValue()).ifPresent(value ->
                            turAemTargetAttrValueMap.addWithSingleValue(facet,
                                    addTagToAttrValueList(context, turAemSourceContext, facet, value), false)
                    );
                }));
    }

    private TurAemTargetAttrValueMap attributeByClass(TurAemContext context, TurAemSourceContext turAemSourceContext) {
        String className = context.getTurAemSourceAttr().getClassName();
        log.debug("ClassName : {}", className);
        return TurCustomClassCache.getCustomClassMap(className)
                .map(classInstance -> TurAemTargetAttrValueMap.singleItem(context
                                .getTurAemTargetAttr().getName(),
                        ((TurAemExtAttributeInterface) classInstance)
                                .consume(context.getTurAemTargetAttr(),
                                        context.getTurAemSourceAttr(),
                                        (TurAemObject) context.getCmsObjectInstance(),
                                        turAemSourceContext),
                        false))
                .orElseGet(TurAemTargetAttrValueMap::new);

    }

}
