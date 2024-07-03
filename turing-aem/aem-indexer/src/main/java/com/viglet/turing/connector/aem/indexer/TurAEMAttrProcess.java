package com.viglet.turing.connector.aem.indexer;

import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.TurAEMCommonAttrProcess;
import com.viglet.turing.connector.aem.commons.TurAEMCommonsUtils;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.ExtAttributeInterface;
import com.viglet.turing.connector.cms.beans.TurCmsContext;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueList;
import com.viglet.turing.connector.cms.mappers.TurCmsContentDefinitionProcess;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

@Slf4j
public class TurAEMAttrProcess {

    public TurCmsTargetAttrValueList prepareAttributeDefs(AemObject aemObject,
                                                          TurCmsContentDefinitionProcess turCmsContentDefinitionProcess,
                                                          List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                          TurAemSourceContext turAemSourceContext) {
        return turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(aemObject.getType())
                .map(turCmsModel -> {
                    TurCmsContext context = new TurCmsContext(aemObject, turCmsContentDefinitionProcess.getConfig());
                    TurCmsTargetAttrValueList turCmsTargetAttrValues = new TurCmsTargetAttrValueList();
                    turCmsModel.getTargetAttrs().stream().filter(Objects::nonNull)
                            .forEach(targetAttr -> {
                                log.debug(String.format("TargetAttr: %s", targetAttr));
                                context.setTurCmsTargetAttr(targetAttr);
                                if (TurAEMCommonAttrProcess.hasClassNoSource(targetAttr)) {
                                    turCmsTargetAttrValues.addAll(process(context, turSNAttributeSpecList, turAemSourceContext, aemObject));
                                } else {
                                    targetAttr.getSourceAttrs().stream().filter(Objects::nonNull)
                                            .forEach(sourceAttr ->
                                                    turCmsTargetAttrValues.addAll(
                                                            addTargetAttrValuesBySourceAttr(turAemSourceContext, turSNAttributeSpecList,
                                                                    targetAttr, sourceAttr, context, aemObject)));
                                }
                            });
                    return turCmsTargetAttrValues;
                }).orElseGet(() -> {
                    log.error(STR."Content Type not found: \{aemObject.getType()}");
                    return new TurCmsTargetAttrValueList();
                });
    }

    private TurCmsTargetAttrValueList addTargetAttrValuesBySourceAttr(TurAemSourceContext turAemSourceContext,
                                                                      List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                                      TurCmsTargetAttr targetAttr,
                                                                      TurCmsSourceAttr sourceAttr,
                                                                      TurCmsContext context,
                                                                      AemObject aemObject) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        try {
            context.setTurCmsSourceAttr(sourceAttr);
            TurCmsTargetAttrValueList targetAttrValues = process(
                    context, turSNAttributeSpecList, turAemSourceContext, aemObject);
            if (sourceAttr.isUniqueValues()) {
                turCmsTargetAttrValueList.add(TurAEMCommonAttrProcess.getTurAttrDefUnique(targetAttr,
                        targetAttrValues));
            } else {
                turCmsTargetAttrValueList.addAll(targetAttrValues);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return turCmsTargetAttrValueList;
    }

    private TurCmsTargetAttrValueList process(TurCmsContext context,
                                              List<TurSNAttributeSpec> turSNAttributeSpecList,
                                              TurAemSourceContext turAemSourceContext,
                                              AemObject aemObject) {
        log.debug(String.format("Target Attribute Name: %s and Source Attribute Name: %s",
                context.getTurCmsTargetAttr().getName(), context.getTurCmsSourceAttr().getName()));
        if (TurAEMCommonAttrProcess.hasTextValue(context.getTurCmsTargetAttr())) {
            return TurCmsTargetAttrValueList.singleItem(context.getTurCmsTargetAttr());
        } else {
            return TurAEMCommonAttrProcess.hasCustomClass(context) ?
                    attributeByClass(context, turAemSourceContext) :
                    attributeByCMS(context, turSNAttributeSpecList, turAemSourceContext, aemObject);
        }
    }

    private TurCmsTargetAttrValueList attributeByCMS(TurCmsContext context,
                                                     List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                     TurAemSourceContext turAemSourceContext,
                                                     AemObject aemObject) {
        String sourceAttrName = context.getTurCmsSourceAttr().getName();
        final Object jcrProperty = TurAEMCommonAttrProcess.getJcrProperty(context, sourceAttrName);
        return TurAEMCommonAttrProcess.hasJcrPropertyValue(jcrProperty) ?
                getTargetAttrValueListFromJcrProperty(context, turAemSourceContext, turSNAttributeSpecList,
                        sourceAttrName, jcrProperty, aemObject) :
                new TurCmsTargetAttrValueList();
    }

    @NotNull
    private TurCmsTargetAttrValueList getTargetAttrValueListFromJcrProperty(TurCmsContext context,
                                                                            TurAemSourceContext turAemSourceContext,
                                                                            List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                                            String sourceAttrName,
                                                                            Object jcrProperty,
                                                                            AemObject aemObject) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        turCmsTargetAttrValueList.addAll(generateNewAttributesFromCqTags(turAemSourceContext, turSNAttributeSpecList,
                sourceAttrName, jcrProperty, aemObject));
        turCmsTargetAttrValueList.addAll(TurAEMCommonAttrProcess.addValuesToAttributes(context.getTurCmsTargetAttr(),
                context.getTurCmsSourceAttr(), jcrProperty));
        return turCmsTargetAttrValueList;
    }

    private TurCmsTargetAttrValueList generateNewAttributesFromCqTags(TurAemSourceContext turAemSourceContext,
                                                                      List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                                      String attributeName,
                                                                      Object jcrProperty,
                                                                      AemObject aemObject) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        if (TurAEMCommonAttrProcess.CQ_TAGS.equals(attributeName)) {
            Optional.ofNullable((JSONArray) jcrProperty).ifPresent(property ->
                    property.forEach(tag -> {
                        String[] tagSplit = tag.toString().split(TurAEMCommonAttrProcess.TAG_SEPARATOR);
                        if (tagSplit.length >= 2) {
                            Optional.ofNullable(tagSplit[0]).ifPresent(facet -> {
                                turSNAttributeSpecList.add(setTagFacet(turAemSourceContext, facet));
                                Optional.ofNullable(tagSplit[1]).ifPresent(value ->
                                        turCmsTargetAttrValueList.addWithSingleValue(facet,
                                                addTagToAttrValueList(turAemSourceContext, facet, value, aemObject))
                                );
                            });
                        }
                    })
            );
        }
        return turCmsTargetAttrValueList;
    }

    private static String addTagToAttrValueList(TurAemSourceContext turAemSourceContext,
                                                String facet, String value, AemObject aemObject) {
        JSONObject infinityJson = TurAEMCommonsUtils
                .getInfinityJson(STR."/content/_cq_tags/\{facet}/\{value}",
                        turAemSourceContext);
        Locale locale = TurAEMCommonsUtils.getLocaleFromAemObject(turAemSourceContext, aemObject);
        String titleLocale = locale.toString().toLowerCase();
        String titleLanguage = locale.getLanguage().toLowerCase();
        Map<String, String> tagLabels = TurAEMCommonAttrProcess.getTagLabels(infinityJson);
        if (tagLabels.containsKey(titleLocale))
            return tagLabels.get(titleLocale);
        else if (tagLabels.containsKey(titleLanguage))
            return tagLabels.get(titleLanguage);
        else return tagLabels.getOrDefault(TurAEMCommonAttrProcess.DEFAULT, value);
    }

    private static TurSNAttributeSpec setTagFacet(TurAemSourceContext turAemSourceContext,
                                                  String facetId) {
        JSONObject tagFacet = TurAEMCommonsUtils
                .getInfinityJson(STR."/content/_cq_tags/\{facetId}",
                        turAemSourceContext);
        return TurAEMCommonAttrProcess.getTurSNAttributeSpec(facetId, TurAEMCommonAttrProcess.getTagLabels(tagFacet));
    }


    private TurCmsTargetAttrValueList attributeByClass(TurCmsContext context, TurAemSourceContext turAemSourceContext) {
        String className = context.getTurCmsSourceAttr().getClassName();
        log.debug(STR."ClassName : \{className}");
        try {
            return TurCmsTargetAttrValueList.singleItem(context
                            .getTurCmsTargetAttr().getName(),
                    ((ExtAttributeInterface) Objects.requireNonNull(Class.forName(className)
                            .getDeclaredConstructor().newInstance()))
                            .consume(context.getTurCmsTargetAttr(), context.getTurCmsSourceAttr(),
                                    (AemObject) context.getCmsObjectInstance(), turAemSourceContext));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException | ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        return new TurCmsTargetAttrValueList();
    }
}
