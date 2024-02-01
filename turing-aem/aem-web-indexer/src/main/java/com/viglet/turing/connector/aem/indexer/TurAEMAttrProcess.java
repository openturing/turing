package com.viglet.turing.connector.aem.indexer;

import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.connector.aem.indexer.ext.ExtAttributeInterface;
import com.viglet.turing.connector.cms.beans.TurCmsContext;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValue;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueList;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsContentDefinitionProcess;
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

import java.lang.reflect.InvocationTargetException;
import java.util.*;

@Slf4j
public class TurAEMAttrProcess {
    public static final String JCR_TITLE = "jcr:title";
    public static final String CQ_TAGS = "cq:tags";
    public static final String TAG_SEPARATOR = ":";
    public static final String DEFAULT = "default";

    public TurCmsTargetAttrValueList prepareAttributeDefs(AemObject aemObject,
                                                          TurCmsContentDefinitionProcess turCmsContentDefinitionProcess,
                                                          List<TurSNAttributeSpec> turSNAttributeSpecList, TurAemIndexerTool turAEMIndexerTool) {
        return turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(aemObject.getType())
                .map(turCmsModel -> {
                    TurCmsContext context = new TurCmsContext(aemObject, turCmsContentDefinitionProcess.getConfig());
                    TurCmsTargetAttrValueList turCmsTargetAttrValues = new TurCmsTargetAttrValueList();
                    turCmsModel.getTargetAttrs().stream().filter(Objects::nonNull)
                            .forEach(targetAttr -> {
                                log.debug(String.format("TargetAttr: %s", targetAttr));
                                context.setTurCmsTargetAttr(targetAttr);
                                if (hasClassNoSource(targetAttr)) {
                                    turCmsTargetAttrValues.addAll(process(context, turSNAttributeSpecList, turAEMIndexerTool));
                                } else {
                                    targetAttr.getSourceAttrs().stream().filter(Objects::nonNull)
                                            .forEach(sourceAttr ->
                                                    turCmsTargetAttrValues.addAll(
                                                            addTargetAttrValuesBySourceAttr(turAEMIndexerTool, turSNAttributeSpecList,
                                                                    targetAttr, sourceAttr, context)));
                                }
                            });
                    return turCmsTargetAttrValues;
                }).orElseGet(() -> {
                    log.error("Content Type not found: " + aemObject.getType());
                    return new TurCmsTargetAttrValueList();
                });
    }

    private TurCmsTargetAttrValueList addTargetAttrValuesBySourceAttr(TurAemIndexerTool turAemIndexerTool,
                                                                      List<TurSNAttributeSpec> turSNAttributeSpecList, TurCmsTargetAttr targetAttr,
                                                                      TurCmsSourceAttr sourceAttr,
                                                                      TurCmsContext context) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        try {
            context.setTurCmsSourceAttr(sourceAttr);
            TurCmsTargetAttrValueList targetAttrValues = process(
                    context, turSNAttributeSpecList, turAemIndexerTool);
            if (sourceAttr.isUniqueValues()) {
                turCmsTargetAttrValueList.add(getTurAttrDefUnique(targetAttr,
                        targetAttrValues));
            } else {
                turCmsTargetAttrValueList.addAll(targetAttrValues);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return turCmsTargetAttrValueList;
    }

    private static boolean hasClassNoSource(TurCmsTargetAttr targetAttr) {
        return targetAttr.getSourceAttrs() == null
                && StringUtils.isNotBlank(targetAttr.getClassName());
    }

    private TurCmsTargetAttrValueList process(TurCmsContext context,
                                              List<TurSNAttributeSpec> turSNAttributeSpecList, TurAemIndexerTool turAemIndexerTool) {
        log.debug(String.format("Target Attribute Name: %s and Source Attribute Name: %s",
                context.getTurCmsTargetAttr().getName(), context.getTurCmsSourceAttr().getName()));
        if (hasTextValue(context.getTurCmsTargetAttr())) {
            return TurCmsTargetAttrValueList.singleItem(context.getTurCmsTargetAttr());
        } else {
            return hasCustomClass(context) ?
                    attributeByClass(context) :
                    attributeByCMS(context, turSNAttributeSpecList, turAemIndexerTool);
        }
    }

    private static boolean hasTextValue(TurCmsTargetAttr turCmsTargetAttr) {
        return StringUtils.isNotEmpty(turCmsTargetAttr.getTextValue());
    }

    private TurCmsTargetAttrValueList attributeByCMS(TurCmsContext context,
                                                     List<TurSNAttributeSpec> turSNAttributeSpecList, TurAemIndexerTool turAemIndexerTool) {
        String sourceAttrName = context.getTurCmsSourceAttr().getName();
        final Object jcrProperty = getJcrProperty(context, sourceAttrName);
        return hasJcrPropertyValue(jcrProperty) ?
                getTargetAttrValueListFromJcrProperty(context, turAemIndexerTool, turSNAttributeSpecList, sourceAttrName, jcrProperty) :
                new TurCmsTargetAttrValueList();
    }

    @NotNull
    private TurCmsTargetAttrValueList getTargetAttrValueListFromJcrProperty(TurCmsContext context,
                                                                            TurAemIndexerTool turAemIndexerTool,
                                                                            List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                                            String sourceAttrName,
                                                                            Object jcrProperty) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        turCmsTargetAttrValueList.addAll(generateNewAttributesFromCqTags(context, turAemIndexerTool, turSNAttributeSpecList,
                sourceAttrName, jcrProperty));
        turCmsTargetAttrValueList.addAll(addValuesToAttributes(context.getTurCmsTargetAttr(),
                context.getTurCmsSourceAttr(), jcrProperty));
        return turCmsTargetAttrValueList;
    }

    @Nullable
    private Object getJcrProperty(TurCmsContext context, String sourceAttrName) {
        return Optional.ofNullable(sourceAttrName).map(attrName -> {
            AemObject aemObject = (AemObject) context.getCmsObjectInstance();
            if (aemObject.getJcrContentNode().has(attrName)) {
                return aemObject.getJcrContentNode().get(attrName);
            } else if (aemObject.getAttributes().containsKey(attrName))
                return aemObject.getAttributes().get(attrName);
            return null;
        }).orElse(null);

    }

    private TurCmsTargetAttrValueList generateNewAttributesFromCqTags(TurCmsContext context,
                                                                      TurAemIndexerTool turAemIndexerTool,
                                                                      List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                                      String attributeName,
                                                                      Object jcrProperty) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        if (CQ_TAGS.equals(attributeName)) {
            Optional.ofNullable((JSONArray) jcrProperty).ifPresent(property ->
                    property.forEach(tag -> {
                        String[] tagSplit = tag.toString().split(TAG_SEPARATOR);
                        if (tagSplit.length >= 2) {
                            Optional.ofNullable(tagSplit[0]).ifPresent(facet -> {
                                turSNAttributeSpecList.add(setTagFacet(turAemIndexerTool, facet));
                                Optional.ofNullable(tagSplit[1]).ifPresent(value ->
                                        turCmsTargetAttrValueList.addWithSingleValue(facet,
                                                addTagToAttrValueList(context, turAemIndexerTool, facet, value))
                                );
                            });
                        }
                    })
            );
        }
        return turCmsTargetAttrValueList;
    }

    private static String addTagToAttrValueList(TurCmsContext context, TurAemIndexerTool turAemIndexerTool,
                                                String facet, String value) {
        JSONObject infinityJson = TurAemUtils
                .getInfinityJson("/content/_cq_tags/" + facet + "/" + value,
                        turAemIndexerTool);
        Locale locale = TurAemUtils.getLocaleFromContext(context);
        String titleLocale = locale.toString().toLowerCase();
        String titleLanguage = locale.getLanguage().toLowerCase();
        Map<String, String> tagLabels = getTagLabels(infinityJson);
        if (tagLabels.containsKey(titleLocale))
            return tagLabels.get(titleLocale);
        else if (tagLabels.containsKey(titleLanguage))
            return tagLabels.get(titleLanguage);
        else return tagLabels.getOrDefault(DEFAULT, value);
    }

    private static TurSNAttributeSpec setTagFacet(TurAemIndexerTool turAemIndexerTool,
                                                  String facetId) {
        JSONObject tagFacet = TurAemUtils
                .getInfinityJson("/content/_cq_tags/" + facetId,
                        turAemIndexerTool);
        return getTurSNAttributeSpec(facetId, getTagLabels(tagFacet));
    }

    @NotNull
    private static TurSNAttributeSpec getTurSNAttributeSpec(String facet, Map<String, String> facetLabel) {
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

    private static Map<String, String> getTagLabels(JSONObject tagJson) {
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

    private TurCmsTargetAttrValueList addValuesToAttributes(TurCmsTargetAttr turCmsTargetAttr,
                                                            TurCmsSourceAttr turCmsSourceAttr,
                                                            Object jcrProperty) {

        if (turCmsSourceAttr.isConvertHtmlToText()) {
            return TurCmsTargetAttrValueList.singleItem(turCmsTargetAttr.getName(),
                    HtmlManipulator.html2Text(TurAemUtils.getPropertyValue(jcrProperty)));
        } else if (jcrProperty != null) {
            TurMultiValue turMultiValue = new TurMultiValue();
            if (isJSONArray(jcrProperty)) {
                ((JSONArray) jcrProperty).forEach(item -> turMultiValue.add(item.toString()));
            } else {
                turMultiValue.add(TurAemUtils.getPropertyValue(jcrProperty));
            }
            if (!turMultiValue.isEmpty()) {
                return TurCmsTargetAttrValueList.singleItem(turCmsTargetAttr.getName(), turMultiValue);
            }
        }
        return new TurCmsTargetAttrValueList();
    }

    private TurCmsTargetAttrValueList attributeByClass(TurCmsContext context) {
        String className = context.getTurCmsSourceAttr().getClassName();
        log.debug("ClassName : " + className);
        try {
            return TurCmsTargetAttrValueList.singleItem(context
                            .getTurCmsTargetAttr().getName(),
                    ((ExtAttributeInterface) Objects.requireNonNull(Class.forName(className)
                            .getDeclaredConstructor().newInstance()))
                            .consume(context.getTurCmsTargetAttr(), context.getTurCmsSourceAttr(),
                                    (AemObject) context.getCmsObjectInstance(), context.getConfiguration()));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException | ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        return new TurCmsTargetAttrValueList();
    }

    private boolean isJSONArray(Object jcrProperty) {
        return (jcrProperty instanceof JSONArray)
                && !((JSONArray) jcrProperty).isEmpty();
    }

    private boolean hasCustomClass(TurCmsContext context) {
        return StringUtils.isNotBlank(context.getTurCmsSourceAttr().getClassName());
    }

    private boolean hasJcrPropertyValue(Object jcrProperty) {
        return ObjectUtils.allNotNull(jcrProperty, TurAemUtils.getPropertyValue(jcrProperty));
    }

    private TurCmsTargetAttrValue getTurAttrDefUnique(TurCmsTargetAttr turCmsTargetAttr,
                                                      TurCmsTargetAttrValueList turCmsTargetAttrValueList) {
        TurMultiValue multiValue = new TurMultiValue();
        turCmsTargetAttrValueList.stream()
                .flatMap(targetAttrValue -> targetAttrValue.getMultiValue().stream())
                .distinct().forEach(multiValue::add);
        return new TurCmsTargetAttrValue(turCmsTargetAttr.getName(), multiValue);
    }
}
