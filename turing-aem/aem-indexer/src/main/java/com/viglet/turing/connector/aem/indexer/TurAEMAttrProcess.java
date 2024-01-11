package com.viglet.turing.connector.aem.indexer;

import com.viglet.turing.connector.aem.indexer.ext.ExtAttributeInterface;
import com.viglet.turing.connector.cms.beans.TurCmsContext;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValue;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsContentDefinitionProcess;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import com.viglet.turing.connector.cms.util.HtmlManipulator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

@Slf4j
public class TurAEMAttrProcess {
    public static final String JCR_TITLE = "jcr:title";
    public static final String CQ_TAGS = "cq:tags";

    public List<TurCmsTargetAttrValue> prepareAttributeDefs(AemObject aemObject,
                                                            TurCmsContentDefinitionProcess turCmsContentDefinitionProcess,
                                                            TurAEMIndexerTool turAEMIndexerTool) {
        return Optional.ofNullable(turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(aemObject.getType()))
                .map(turCmsModel -> {
                    List<TurCmsTargetAttrValue> turCmsTargetAttrValues = new ArrayList<>();
                    turCmsModel.getTargetAttrs().stream()
                            .filter(Objects::nonNull).forEach(targetAttr -> {
                                log.debug(String.format("TargetAttr: %s", targetAttr));
                                if (targetAttr.getSourceAttrs() == null
                                        && StringUtils.isNotBlank(targetAttr.getClassName())) {
                                    List<TurCmsTargetAttrValue> targetAttrValues = process(
                                            new TurCmsContext(aemObject, targetAttr, null,
                                                    turCmsContentDefinitionProcess.getConfig()),
                                            turAEMIndexerTool);
                                    turCmsTargetAttrValues.addAll(targetAttrValues);
                                } else {
                                    targetAttr.getSourceAttrs().stream()
                                            .filter(Objects::nonNull)
                                            .forEach(sourceAttr -> {
                                                try {
                                                    List<TurCmsTargetAttrValue> targetAttrValues = process(
                                                            new TurCmsContext(aemObject, targetAttr, sourceAttr,
                                                                    turCmsContentDefinitionProcess.getConfig()),
                                                            turAEMIndexerTool);
                                                    if (sourceAttr.isUniqueValues()) {
                                                        turCmsTargetAttrValues.add(getTurAttrDefUnique(targetAttr,
                                                                targetAttrValues));
                                                    } else {
                                                        turCmsTargetAttrValues.addAll(targetAttrValues);
                                                    }
                                                } catch (Exception e) {
                                                    log.error(e.getMessage(), e);
                                                }
                                            });
                                }
                            });

                    return turCmsTargetAttrValues;
                }).orElseGet(() -> {
                    log.error(STR."Content Type not found: \{aemObject.getType()}");
                    return Collections.emptyList();
                });
    }

    private List<TurCmsTargetAttrValue> process(TurCmsContext context,
                                                      TurAEMIndexerTool turAEMIndexerTool) {
        log.debug(String.format("Target Attribute Name: %s and Source Attribute Name: %s",
                context.getTurCmsTargetAttr().getName(), context.getTurCmsSourceAttr().getName()));
        if (hasTextValue(context.getTurCmsTargetAttr())) {
            return setLiteralTextValueToAttribute(context.getTurCmsTargetAttr());
        } else {
            return hasCustomClass(context) ?
                    attributeByClass(context) :
                    attributeByCMS(context, turAEMIndexerTool);
        }
    }

    private static boolean hasTextValue(TurCmsTargetAttr turCmsTargetAttr) {
        return StringUtils.isNotEmpty(turCmsTargetAttr.getTextValue());
    }

    private static List<TurCmsTargetAttrValue> setLiteralTextValueToAttribute(TurCmsTargetAttr turCmsTargetAttr) {
        List<TurCmsTargetAttrValue> turCmsTargetAttrValues = new ArrayList<>();
        TurCmsTargetAttrValue turCmsTargetAttrValue = new TurCmsTargetAttrValue(turCmsTargetAttr.getName(),
                TurMultiValue.singleItem(turCmsTargetAttr.getTextValue()));
        turCmsTargetAttrValues.add(turCmsTargetAttrValue);
        return turCmsTargetAttrValues;
    }

    private List<TurCmsTargetAttrValue> attributeByCMS(TurCmsContext context,
                                                              TurAEMIndexerTool turAEMIndexerTool) {
        List<TurCmsTargetAttrValue> turCmsTargetAttrValues = new ArrayList<>();
        String sourceAttrName = context.getTurCmsSourceAttr().getName();
        Object jcrProperty = null;
        if (sourceAttrName != null) {
            AemObject aemObject = (AemObject) context.getCmsObjectInstance();
            if (aemObject.getJcrContentNode().has(sourceAttrName)) {
                jcrProperty = aemObject.getJcrContentNode().get(sourceAttrName);
                generateNewAttributesFromCqTags(turAEMIndexerTool, sourceAttrName, jcrProperty, turCmsTargetAttrValues);
            } else if (aemObject.getAttributes().containsKey(sourceAttrName))
                jcrProperty = aemObject.getAttributes().get(sourceAttrName);
        }
        if (hasJcrPropertyValue(jcrProperty)) {
            turCmsTargetAttrValues.addAll(addValuesToAttributes(context.getTurCmsTargetAttr(),
                    context.getTurCmsSourceAttr(), jcrProperty));
            return turCmsTargetAttrValues;
        }
        return Collections.emptyList();
    }

    private void generateNewAttributesFromCqTags(TurAEMIndexerTool turAEMIndexerTool,
                                                        String attributeName,
                                                        Object jcrProperty,
                                                        List<TurCmsTargetAttrValue> turCmsTargetAttrValues) {
        if (CQ_TAGS.equals(attributeName)) {
            Optional.ofNullable((JSONArray) jcrProperty).ifPresent(property ->
                    property.forEach(tag -> {
                        String[] tagSplit = tag.toString().split(":");
                        if (tagSplit.length >= 2) {
                            JSONObject infinityJson = TurAemUtils
                                    .getInfinityJson(STR."/content/_cq_tags/\{String.join("/", tagSplit)}",
                                            turAEMIndexerTool.getHostAndPort(),
                                            turAEMIndexerTool.getUsername(),
                                            turAEMIndexerTool.getPassword());
                            Optional.ofNullable(tagSplit[1]).ifPresent(value ->
                                    turCmsTargetAttrValues.add(new TurCmsTargetAttrValue(tagSplit[0],
                                            TurMultiValue.singleItem(infinityJson.has(JCR_TITLE) ?
                                                    infinityJson.getString(JCR_TITLE) :
                                                    value)
                                    )));
                        }
                    })
            );
        }
    }

    private Collection<? extends TurCmsTargetAttrValue> addValuesToAttributes(TurCmsTargetAttr turCmsTargetAttr,
                                                                                     TurCmsSourceAttr turCmsSourceAttr,
                                                                                     Object jcrProperty) {
        List<TurCmsTargetAttrValue> turCmsTargetAttrValues = new ArrayList<>();
        TurMultiValue turMultiValue = new TurMultiValue();
        if (turCmsSourceAttr.isConvertHtmlToText()) {
            turMultiValue.add(HtmlManipulator.html2Text(TurAemUtils.getPropertyValue(jcrProperty)));
            turCmsTargetAttrValues.add(new TurCmsTargetAttrValue(turCmsTargetAttr.getName(), turMultiValue));
        } else if (jcrProperty != null) {
            if (isJSONArray(jcrProperty)) {
                ((JSONArray) jcrProperty).forEach(item -> turMultiValue.add(item.toString()));
            } else {
                turMultiValue.add(TurAemUtils.getPropertyValue(jcrProperty));
            }
            if (!turMultiValue.isEmpty()) {
                turCmsTargetAttrValues.add(new TurCmsTargetAttrValue(turCmsTargetAttr.getName(), turMultiValue));
            }
        }
        return turCmsTargetAttrValues;
    }

    private List<TurCmsTargetAttrValue> attributeByClass(TurCmsContext context) {
        List<TurCmsTargetAttrValue> turCmsTargetAttrValues = new ArrayList<>();
        String className = context.getTurCmsSourceAttr().getClassName();
        log.debug(STR."ClassName : \{className}");

        Object extAttribute = null;
        try {
            extAttribute = Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException | ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        TurMultiValue turMultiValue = ((ExtAttributeInterface) Objects.requireNonNull(extAttribute))
                .consume(context.getTurCmsTargetAttr(), context.getTurCmsSourceAttr(),
                        (AemObject) context.getCmsObjectInstance(), context.getConfiguration());
        TurCmsTargetAttrValue turCmsTargetAttrValue = new TurCmsTargetAttrValue(context
                .getTurCmsTargetAttr().getName(), turMultiValue);
        turCmsTargetAttrValues.add(turCmsTargetAttrValue);
        return turCmsTargetAttrValues;
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
                                                             List<TurCmsTargetAttrValue> targetAttrValues) {
        TurMultiValue multiValue = new TurMultiValue();
        targetAttrValues.stream().flatMap(targetAttrValue ->
                targetAttrValue.getMultiValue().stream()).distinct().forEach(multiValue::add);
        return new TurCmsTargetAttrValue(turCmsTargetAttr.getName(), multiValue);
    }
}
