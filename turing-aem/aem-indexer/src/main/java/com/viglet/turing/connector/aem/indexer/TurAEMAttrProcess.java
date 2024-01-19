package com.viglet.turing.connector.aem.indexer;

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
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class TurAEMAttrProcess {
    public static final String JCR_TITLE = "jcr:title";
    public static final String CQ_TAGS = "cq:tags";

    public TurCmsTargetAttrValueList prepareAttributeDefs(AemObject aemObject,
                                                            TurCmsContentDefinitionProcess turCmsContentDefinitionProcess,
                                                            TurAEMIndexerTool turAEMIndexerTool) {
        return turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(aemObject.getType())
                .map(turCmsModel -> {
                    TurCmsContext context = new TurCmsContext(aemObject, turCmsContentDefinitionProcess.getConfig());
                    TurCmsTargetAttrValueList turCmsTargetAttrValues = new TurCmsTargetAttrValueList();
                    turCmsModel.getTargetAttrs().stream().filter(Objects::nonNull)
                            .forEach(targetAttr -> {
                                log.debug(String.format("TargetAttr: %s", targetAttr));
                                context.setTurCmsTargetAttr(targetAttr);
                                if (hasClassNoSource(targetAttr)) {
                                    turCmsTargetAttrValues.addAll(process(context, turAEMIndexerTool));
                                } else {
                                    targetAttr.getSourceAttrs().stream().filter(Objects::nonNull)
                                            .forEach(sourceAttr ->
                                                    turCmsTargetAttrValues.addAll(
                                                            addTargetAttrValuesBySourceAttr(turAEMIndexerTool,
                                                                    targetAttr, sourceAttr, context)));
                                }
                            });
                    return turCmsTargetAttrValues;
                }).orElseGet(() -> {
                    log.error(STR."Content Type not found: \{aemObject.getType()}");
                    return new TurCmsTargetAttrValueList();
                });
    }

    private TurCmsTargetAttrValueList addTargetAttrValuesBySourceAttr(TurAEMIndexerTool turAEMIndexerTool,
                                                                      TurCmsTargetAttr targetAttr,
                                                                      TurCmsSourceAttr sourceAttr,
                                                                      TurCmsContext context) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        try {
            context.setTurCmsSourceAttr(sourceAttr);
            TurCmsTargetAttrValueList targetAttrValues = process(
                    context, turAEMIndexerTool);
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
                                              TurAEMIndexerTool turAEMIndexerTool) {
        log.debug(String.format("Target Attribute Name: %s and Source Attribute Name: %s",
                context.getTurCmsTargetAttr().getName(), context.getTurCmsSourceAttr().getName()));
        if (hasTextValue(context.getTurCmsTargetAttr())) {
            return TurCmsTargetAttrValueList.singleItem(context.getTurCmsTargetAttr());
        } else {
            return hasCustomClass(context) ?
                    attributeByClass(context) :
                    attributeByCMS(context, turAEMIndexerTool);
        }
    }

    private static boolean hasTextValue(TurCmsTargetAttr turCmsTargetAttr) {
        return StringUtils.isNotEmpty(turCmsTargetAttr.getTextValue());
    }

    private TurCmsTargetAttrValueList attributeByCMS(TurCmsContext context,
                                                     TurAEMIndexerTool turAEMIndexerTool) {
        String sourceAttrName = context.getTurCmsSourceAttr().getName();
        final Object jcrProperty = getJcrProperty(context, sourceAttrName);
        return hasJcrPropertyValue(jcrProperty) ?
                getTargetAttrValueListFromJcrProperty(context, turAEMIndexerTool, sourceAttrName, jcrProperty) :
                new TurCmsTargetAttrValueList();
    }

    @NotNull
    private TurCmsTargetAttrValueList getTargetAttrValueListFromJcrProperty(TurCmsContext context,
                                                                            TurAEMIndexerTool turAEMIndexerTool,
                                                                            String sourceAttrName,
                                                                            Object jcrProperty) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        turCmsTargetAttrValueList.addAll(generateNewAttributesFromCqTags(context, turAEMIndexerTool,
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
                                                                      TurAEMIndexerTool turAEMIndexerTool,
                                                                      String attributeName,
                                                                      Object jcrProperty) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        if (CQ_TAGS.equals(attributeName)) {
            Optional.ofNullable((JSONArray) jcrProperty).ifPresent(property ->
                    property.forEach(tag -> {
                        String[] tagSplit = tag.toString().split(":");
                        if (tagSplit.length >= 2) {
                            JSONObject infinityJson = TurAemUtils
                                    .getInfinityJson(STR."/content/_cq_tags/\{String.join("/", tagSplit)}",
                                            turAEMIndexerTool);
                            Optional.ofNullable(tagSplit[1]).ifPresent(value ->
                                    turCmsTargetAttrValueList.addWithSingleValue(tagSplit[0],
                                            getTagLabel(context, value, infinityJson))
                            );
                        }
                    })
            );
        }
        return turCmsTargetAttrValueList;
    }

    private static String getTagLabel(TurCmsContext context, String value, JSONObject infinityJson) {
        Locale locale = TurAemUtils.getLocaleFromContext(context);
        String titleLocale = STR."\{JCR_TITLE}.\{locale.toLanguageTag().toLowerCase()}";
        String titleLanguage = STR."\{JCR_TITLE}.\{locale.getLanguage().toLowerCase()}";
        if (infinityJson.has(titleLocale))
            return infinityJson.getString(titleLocale);
        else if (infinityJson.has(titleLanguage))
            return infinityJson.getString(titleLanguage);
        else if (infinityJson.has(JCR_TITLE))
            return infinityJson.getString(JCR_TITLE);
        else
            return value;

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
        log.debug(STR."ClassName : \{className}");
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
        turCmsTargetAttrValueList.stream().flatMap(targetAttrValue ->
                targetAttrValue.getMultiValue().stream()).distinct().forEach(multiValue::add);
        return new TurCmsTargetAttrValue(turCmsTargetAttr.getName(), multiValue);
    }
}
