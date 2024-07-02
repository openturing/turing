package com.viglet.turing.connector.aem.indexer;

import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.TurAEMCommonAttrProcess;
import com.viglet.turing.connector.aem.indexer.ext.ExtAttributeInterface;
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
                                                          List<TurSNAttributeSpec> turSNAttributeSpecList, TurAEMIndexerTool turAEMIndexerTool) {
        return turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(aemObject.getType())
                .map(turCmsModel -> {
                    TurCmsContext context = new TurCmsContext(aemObject, turCmsContentDefinitionProcess.getConfig());
                    TurCmsTargetAttrValueList turCmsTargetAttrValues = new TurCmsTargetAttrValueList();
                    turCmsModel.getTargetAttrs().stream().filter(Objects::nonNull)
                            .forEach(targetAttr -> {
                                log.debug(String.format("TargetAttr: %s", targetAttr));
                                context.setTurCmsTargetAttr(targetAttr);
                                if (TurAEMCommonAttrProcess.hasClassNoSource(targetAttr)) {
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
                    log.error(STR."Content Type not found: \{aemObject.getType()}");
                    return new TurCmsTargetAttrValueList();
                });
    }

    private TurCmsTargetAttrValueList addTargetAttrValuesBySourceAttr(TurAEMIndexerTool turAEMIndexerTool,
                                                                      List<TurSNAttributeSpec> turSNAttributeSpecList, TurCmsTargetAttr targetAttr,
                                                                      TurCmsSourceAttr sourceAttr,
                                                                      TurCmsContext context) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        try {
            context.setTurCmsSourceAttr(sourceAttr);
            TurCmsTargetAttrValueList targetAttrValues = process(
                    context, turSNAttributeSpecList, turAEMIndexerTool);
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
                                              List<TurSNAttributeSpec> turSNAttributeSpecList, TurAEMIndexerTool turAEMIndexerTool) {
        log.debug(String.format("Target Attribute Name: %s and Source Attribute Name: %s",
                context.getTurCmsTargetAttr().getName(), context.getTurCmsSourceAttr().getName()));
        if (TurAEMCommonAttrProcess.hasTextValue(context.getTurCmsTargetAttr())) {
            return TurCmsTargetAttrValueList.singleItem(context.getTurCmsTargetAttr());
        } else {
            return TurAEMCommonAttrProcess.hasCustomClass(context) ?
                    attributeByClass(context, turAEMIndexerTool) :
                    attributeByCMS(context, turSNAttributeSpecList, turAEMIndexerTool);
        }
    }


    private TurCmsTargetAttrValueList attributeByCMS(TurCmsContext context,
                                                     List<TurSNAttributeSpec> turSNAttributeSpecList, TurAEMIndexerTool turAEMIndexerTool) {
        String sourceAttrName = context.getTurCmsSourceAttr().getName();
        final Object jcrProperty = TurAEMCommonAttrProcess.getJcrProperty(context, sourceAttrName);
        return TurAEMCommonAttrProcess.hasJcrPropertyValue(jcrProperty) ?
                getTargetAttrValueListFromJcrProperty(context, turAEMIndexerTool, turSNAttributeSpecList, sourceAttrName, jcrProperty) :
                new TurCmsTargetAttrValueList();
    }

    @NotNull
    private TurCmsTargetAttrValueList getTargetAttrValueListFromJcrProperty(TurCmsContext context,
                                                                            TurAEMIndexerTool turAEMIndexerTool,
                                                                            List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                                            String sourceAttrName,
                                                                            Object jcrProperty) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        turCmsTargetAttrValueList.addAll(generateNewAttributesFromCqTags(context, turAEMIndexerTool, turSNAttributeSpecList,
                sourceAttrName, jcrProperty));
        turCmsTargetAttrValueList.addAll(TurAEMCommonAttrProcess.addValuesToAttributes(context.getTurCmsTargetAttr(),
                context.getTurCmsSourceAttr(), jcrProperty));
        return turCmsTargetAttrValueList;
    }


    private TurCmsTargetAttrValueList generateNewAttributesFromCqTags(TurCmsContext context,
                                                                      TurAEMIndexerTool turAEMIndexerTool,
                                                                      List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                                      String attributeName,
                                                                      Object jcrProperty) {
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = new TurCmsTargetAttrValueList();
        if (TurAEMCommonAttrProcess.CQ_TAGS.equals(attributeName)) {
            Optional.ofNullable((JSONArray) jcrProperty).ifPresent(property ->
                    property.forEach(tag -> {
                        String[] tagSplit = tag.toString().split(TurAEMCommonAttrProcess.TAG_SEPARATOR);
                        if (tagSplit.length >= 2) {
                            Optional.ofNullable(tagSplit[0]).ifPresent(facet -> {
                                turSNAttributeSpecList.add(setTagFacet(turAEMIndexerTool, facet));
                                Optional.ofNullable(tagSplit[1]).ifPresent(value ->
                                        turCmsTargetAttrValueList.addWithSingleValue(facet,
                                                addTagToAttrValueList(context, turAEMIndexerTool, facet, value))
                                );
                            });
                        }
                    })
            );
        }
        return turCmsTargetAttrValueList;
    }

    private static String addTagToAttrValueList(TurCmsContext context, TurAEMIndexerTool turAEMIndexerTool,
                                                String facet, String value) {
        JSONObject infinityJson = TurAemUtils
                .getInfinityJson(STR."/content/_cq_tags/\{facet}/\{value}",
                        turAEMIndexerTool);
        Locale locale = TurAemUtils.getLocaleFromContext(context);
        String titleLocale = locale.toString().toLowerCase();
        String titleLanguage = locale.getLanguage().toLowerCase();
        Map<String, String> tagLabels = TurAEMCommonAttrProcess.getTagLabels(infinityJson);
        if (tagLabels.containsKey(titleLocale))
            return tagLabels.get(titleLocale);
        else if (tagLabels.containsKey(titleLanguage))
            return tagLabels.get(titleLanguage);
        else return tagLabels.getOrDefault(TurAEMCommonAttrProcess.DEFAULT, value);
    }

    private static TurSNAttributeSpec setTagFacet(TurAEMIndexerTool turAEMIndexerTool,
                                                  String facetId) {
        JSONObject tagFacet = TurAemUtils
                .getInfinityJson(STR."/content/_cq_tags/\{facetId}",
                        turAEMIndexerTool);
        return TurAEMCommonAttrProcess.getTurSNAttributeSpec(facetId, TurAEMCommonAttrProcess.getTagLabels(tagFacet));
    }


    private TurCmsTargetAttrValueList attributeByClass(TurCmsContext context, TurAEMIndexerTool turAEMIndexerTool) {
        String className = context.getTurCmsSourceAttr().getClassName();
        log.debug(STR."ClassName : \{className}");
        try {
            return TurCmsTargetAttrValueList.singleItem(context
                            .getTurCmsTargetAttr().getName(),
                    ((ExtAttributeInterface) Objects.requireNonNull(Class.forName(className)
                            .getDeclaredConstructor().newInstance()))
                            .consume(context.getTurCmsTargetAttr(), context.getTurCmsSourceAttr(),
                                    (AemObject) context.getCmsObjectInstance(), context.getConfiguration(), turAEMIndexerTool));
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException | ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        return new TurCmsTargetAttrValueList();
    }
}
