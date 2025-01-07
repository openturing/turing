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

package com.viglet.turing.connector.plugin.aem;

import com.google.inject.Inject;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.connector.aem.commons.TurAemAttrProcess;
import com.viglet.turing.connector.aem.commons.TurAemCommonsUtils;
import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.bean.TurAemTargetAttrValueMap;
import com.viglet.turing.connector.aem.commons.config.IAemConfiguration;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.mappers.*;
import com.viglet.turing.connector.commons.plugin.TurConnectorContext;
import com.viglet.turing.connector.commons.plugin.TurConnectorSource;
import com.viglet.turing.connector.plugin.aem.conf.AemPluginHandlerConfiguration;
import com.viglet.turing.connector.plugin.aem.persistence.model.*;
import com.viglet.turing.connector.plugin.aem.persistence.repository.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.viglet.turing.commons.sn.field.TurSNFieldName.ID;


/**
 * @author Alexandre Oliveira
 * @since 0.3.10
 */
@Slf4j
@Getter
@Component
public class TurAemPluginProcess {
    public static final String CONTENT_FRAGMENT = "content-fragment";
    public static final String STATIC_FILE = "static-file";
    public static final String SITE = "site";
    public static final String DATA_MASTER = "data/master";
    public static final String METADATA = "metadata";
    public static final String JCR = "jcr:";
    public static final String STATIC_FILE_SUB_TYPE = "STATIC_FILE";
    private static final String CQ_PAGE = "cq:Page";
    private static final String DAM_ASSET = "dam:Asset";
    public static final String CQ = "cq:";
    public static final String AEM = "AEM";
    private final String deltaId = UUID.randomUUID().toString();
    private final Set<String> visitedLinks = new HashSet<>();
    private final Queue<String> remainingLinks = new LinkedList<>();
    private final TurAemAttributeSpecificationRepository turAemAttributeSpecificationRepository;
    private String siteName;
    public static final String REP = "rep:";
    private final TurAemPluginIndexingRepository turAemIndexingRepository;
    private final TurAemPluginSystemRepository turAemSystemRepository;
    private final TurAemConfigVarRepository turAemConfigVarRepository;
    private final TurAemSourceLocalePathRepository turAemSourceLocalePathRepository;
    private final TurAemPluginModelRepository turAemPluginModelRepository;
    private final TurAemSourceRepository turAemSourceRepository;
    private final TurAemTargetAttributeRepository turAemTargetAttributeRepository;
    private TurConnectorContext turConnectorContext;
    private IAemConfiguration config = null;
    private TurAemContentDefinitionProcess turAemContentDefinitionProcess;
    // Legacy
    private static final boolean REINDEX = false;
    private static final boolean REINDEX_ONCE = false;
    private static final boolean DRY_RUN = false;
    private static final boolean DELIVERED = false;

    @Inject
    public TurAemPluginProcess(TurAemPluginIndexingRepository turAemPluginIndexingRepository,
                               TurAemPluginSystemRepository turAemPluginSystemRepository,
                               TurAemConfigVarRepository turAemConfigVarRepository,
                               TurAemSourceLocalePathRepository turAemSourceLocalePathRepository,
                               TurAemPluginModelRepository turAemPluginModelRepository,
                               TurAemSourceRepository turAemSourceRepository,
                               TurAemAttributeSpecificationRepository turAemAttributeSpecificationRepository,
                               TurAemTargetAttributeRepository turAemTargetAttributeRepository) {
        this.turAemIndexingRepository = turAemPluginIndexingRepository;
        this.turAemSystemRepository = turAemPluginSystemRepository;
        this.turAemConfigVarRepository = turAemConfigVarRepository;
        this.turAemSourceLocalePathRepository = turAemSourceLocalePathRepository;
        this.turAemPluginModelRepository = turAemPluginModelRepository;
        this.turAemSourceRepository = turAemSourceRepository;
        this.turAemAttributeSpecificationRepository = turAemAttributeSpecificationRepository;
        this.turAemTargetAttributeRepository = turAemTargetAttributeRepository;
    }

    public void run(TurAemSource turAemSource, TurConnectorContext turConnectorContext) {
        this.turConnectorContext = turConnectorContext;
        this.turConnectorContext.startIndexing(new TurConnectorSource(turAemSource.getId(),
                turAemSource.getTurSNSites(), AEM, turAemSource.getLocale()));
        config = new AemPluginHandlerConfiguration(turAemSource);

        turAemContentDefinitionProcess = new TurAemContentDefinitionProcess(getTurAemContentMapping(turAemSource));
        TurAemSourceContext turAemSourceContext = getTurAemSourceContext(config);
        try {
            if (REINDEX) {
                turAemIndexingRepository.deleteContentsToReindex(turAemSourceContext.getId());
            }
            if (REINDEX_ONCE) {
                turAemIndexingRepository.deleteContentsToReindexOnce(turAemSourceContext.getId());
            }
            this.getNodesFromJson(turAemSourceContext);
            if (!DRY_RUN) {
                deIndexObjects(turAemSourceContext);
                updateSystemOnce(turAemSourceContext);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        finished(turConnectorContext);
    }

    private void finished(TurConnectorContext turConnectorContext) {
        turConnectorContext.finishIndexing();
    }

    private @NotNull TurAemContentMapping getTurAemContentMapping(TurAemSource turAemSource) {
        List<TurAemModel> turAemModels = new ArrayList<>();
        turAemPluginModelRepository.findByTurAemSource(turAemSource).forEach(pluginModel -> {
            List<TurAemTargetAttr> targetAttrs = new ArrayList<>();
            pluginModel.getTargetAttrs().forEach(targetAttr -> {
                List<TurAemSourceAttr> sourceAttrs = new ArrayList<>();
                targetAttr.getSourceAttrs().forEach(sourceAttr ->
                        sourceAttrs.add(TurAemSourceAttr.builder()
                                .className(sourceAttr.getClassName())
                                .name(sourceAttr.getName())
                                .convertHtmlToText(false)
                                .uniqueValues(false)
                                .build()));
                targetAttrs.add(TurAemTargetAttr.builder()
                        .name(targetAttr.getName())
                        .sourceAttrs(sourceAttrs)
                        .build());
            });
            turAemModels.add(TurAemModel.builder()
                    .className(pluginModel.getClassName())
                    .type(pluginModel.getType())
                    .targetAttrs(targetAttrs)
                    .build());
        });
        List<TurSNAttributeSpec> targetAttrDefinitions = new ArrayList<>();
        turAemAttributeSpecificationRepository.findByTurAemSource(turAemSource)
                .ifPresent(attributeSpecifications ->
                        attributeSpecifications.forEach(attributeSpec ->
                                targetAttrDefinitions.add(TurSNAttributeSpec.builder()
                                        .className(attributeSpec.getClassName())
                                        .name(attributeSpec.getName())
                                        .type(attributeSpec.getType())
                                        .facetName(attributeSpec.getFacetNames())
                                        .description(attributeSpec.getDescription())
                                        .mandatory(attributeSpec.isMandatory())
                                        .multiValued(attributeSpec.isMultiValued())
                                        .facet(attributeSpec.isFacet())
                                        .build()
                                )));

        TurAemContentMapping turAemContentMapping = new TurAemContentMapping();
        turAemContentMapping.setDeltaClassName(turAemSource.getDeltaClass());
        turAemContentMapping.setModels(turAemModels);
        turAemContentMapping.setTargetAttrDefinitions(targetAttrDefinitions);
        return turAemContentMapping;
    }

    private TurAemSourceContext getTurAemSourceContext(IAemConfiguration config) {
        TurAemSourceContext turAemSourceContext = TurAemSourceContext.builder()
                .id(config.getCmsGroup())
                .contentType(config.getCmsContentType())
                .defaultLocale(config.getDefaultSNSiteConfig().getLocale())
                .rootPath(config.getCmsRootPath())
                .url(config.getCmsHost())
                .siteName(getSiteName())
                .subType(config.getCmsSubType())
                .turSNSite(config.getDefaultSNSiteConfig().getName())
                .oncePattern(config.getOncePatternPath())
                .providerName(config.getProviderName())
                .password(config.getCmsPassword())
                .urlPrefix(config.getCDAURLPrefix())
                .username(config.getCmsUsername())
                .localePaths(config.getLocales())
                .build();
        if (log.isDebugEnabled()) {
            log.debug("TurAemSourceContext: {}", turAemSourceContext.toString());
        }
        return turAemSourceContext;
    }

    private void updateSystemOnce(TurAemSourceContext turAemSourceContext) {
        turAemSystemRepository.findByConfig(TurAemCommonsUtils.configOnce(turAemSourceContext))
                .ifPresentOrElse(turAemSystem -> {
                            turAemSystem.setBooleanValue(true);
                            turAemSystemRepository.save(turAemSystem);
                        },
                        () -> turAemSystemRepository.save(new TurAemPluginSystem(TurAemCommonsUtils
                                .configOnce(turAemSourceContext), true)));
    }

    private void getNodesFromJson(TurAemSourceContext turAemSourceContext) {
        if (TurAemCommonsUtils.usingContentTypeParameter(turAemSourceContext)) {
            byContentTypeList(turAemSourceContext);
        }
    }

    private void byContentTypeList(TurAemSourceContext turAemSourceContext) {
        turAemContentDefinitionProcess.findByNameFromModelWithDefinition(turAemSourceContext.getContentType())
                .ifPresentOrElse(turAemModel -> byContentType(turAemSourceContext),
                        () -> log.info("{} type is not configured in CTD Mapping file.",
                                turAemSourceContext.getContentType()));
    }

    private void byContentType(TurAemSourceContext turAemSourceContext) {
        TurAemCommonsUtils.getInfinityJson(turAemSourceContext.getRootPath(), turAemSourceContext, false)
                .ifPresent(infinityJson -> {
                    TurAemCommonsUtils.getSiteName(turAemSourceContext, infinityJson).ifPresent(s -> siteName = s);
                    getNodeFromJson(turAemSourceContext.getRootPath(), infinityJson, turAemSourceContext);
                });
    }


    private void getNodeFromJson(String nodePath, JSONObject jsonObject, TurAemSourceContext turAemSourceContext) {
        TurAemObject aemObject = new TurAemObject(nodePath, jsonObject);
        Optional.of(aemObject).ifPresentOrElse(o -> {
            if (TurAemCommonsUtils.isTypeEqualContentType(jsonObject, turAemSourceContext)) {
                turAemContentDefinitionProcess.findByNameFromModelWithDefinition(turAemSourceContext.getContentType())
                        .ifPresent(model ->
                                prepareIndexObject(model, new TurAemObject(nodePath, jsonObject),
                                        turAemContentDefinitionProcess.getTargetAttrDefinitions(), turAemSourceContext));
            }
        }, () -> log.info("AEM object ({}) is null deltaId = {}",
                turAemSourceContext.getId(), deltaId));

        getChildrenFromJson(nodePath, jsonObject, turAemSourceContext);
    }

    private void getChildrenFromJson(String nodePath, JSONObject jsonObject, TurAemSourceContext turAemSourceContext) {
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith(JCR) && !key.startsWith(REP) && !key.startsWith(CQ)
                    && (turAemSourceContext.getSubType().equals(STATIC_FILE_SUB_TYPE)
                    || TurAemCommonsUtils.checkIfFileHasNotImageExtension(key))) {
                String nodePathChild = "%s/%s".formatted(nodePath, key);
                if (!isOnce(turAemSourceContext) || !TurAemCommonsUtils.isOnceConfig(nodePathChild, config)) {
                    TurAemCommonsUtils.getInfinityJson(nodePathChild, turAemSourceContext, false)
                            .ifPresent(infinityJson ->
                                    getNodeFromJson(nodePathChild, infinityJson, turAemSourceContext));
                }
            }
        });
    }

    private boolean isOnce(TurAemSourceContext turAemSourceContext) {
        return turAemSystemRepository.findByConfig(TurAemCommonsUtils.configOnce(turAemSourceContext))
                .map(TurAemPluginSystem::isBooleanValue)
                .orElse(false);
    }

    private void prepareIndexObject(TurAemModel turAemModel, TurAemObject aemObject,
                                    List<TurSNAttributeSpec> targetAttrDefinitions,
                                    TurAemSourceContext turAemSourceContext) {
        String type = Objects.requireNonNull(turAemSourceContext.getContentType());
        if (type.equals(CQ_PAGE)) {
            indexObject(aemObject, turAemModel, targetAttrDefinitions, turAemSourceContext);
        } else if (type.equals(DAM_ASSET) && !StringUtils.isEmpty(turAemModel.getSubType())) {
            if (turAemModel.getSubType().equals(CONTENT_FRAGMENT) && aemObject.isContentFragment()) {
                aemObject.setDataPath(DATA_MASTER);
                indexObject(aemObject, turAemModel, targetAttrDefinitions, turAemSourceContext);
            } else if (turAemModel.getSubType().equals(STATIC_FILE)) {
                aemObject.setDataPath(METADATA);
                indexObject(aemObject, turAemModel, targetAttrDefinitions, turAemSourceContext);
            }
        }
    }

    private void deIndexObjects(TurAemSourceContext turAemSourceContext) {
        turAemIndexingRepository.findContentsShouldBeDeIndexed(turAemSourceContext.getId(), deltaId)
                .ifPresent(contents -> {
                            log.info("DeIndex Content that were removed...");
                            contents.forEach(content -> {
                                log.info("DeIndex {} object from {} group and {} delta",
                                        content.getAemId(), turAemSourceContext.getId(), deltaId);
                                Map<String, Object> attributes = new HashMap<>();
                                attributes.put(ID, content.getAemId());
                                attributes.put(TurSNFieldName.SOURCE_APPS,
                                        IAemConfiguration.DEFAULT_PROVIDER);
                                turConnectorContext.addJobItem(new TurSNJobItem(TurSNJobAction.DELETE,
                                        Collections.singletonList(config.getDefaultSNSiteConfig().getName()),
                                        content.getLocale(), attributes));
                            });
                            turAemIndexingRepository.deleteContentsWereDeIndexed(turAemSourceContext.getId(), deltaId);
                        }
                );
    }

    private boolean objectNeedBeIndexed(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return !StringUtils.isEmpty(aemObject.getPath()) &&
                !turAemIndexingRepository.existsByAemIdAndIndexGroup(aemObject.getPath(), turAemSourceContext.getId());
    }

    private boolean objectNeedBeReIndexed(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return ddlNeedBeReIndexed(aemObject, turAemSourceContext, TurAemCommonsUtils.getDeltaDate(aemObject,
                turAemSourceContext, turAemContentDefinitionProcess));
    }


    private boolean ddlNeedBeReIndexed(TurAemObject aemObject, TurAemSourceContext turAemSourceContext,
                                       Date deltaDate) {
        return !StringUtils.isEmpty(aemObject.getPath()) &&
                turAemIndexingRepository.existsByAemIdAndIndexGroupAndDateNot(aemObject.getPath(),
                        turAemSourceContext.getId(), deltaDate);
    }

    private void indexObject(@NotNull TurAemObject aemObject, TurAemModel turAemModel,
                             List<TurSNAttributeSpec> turSNAttributeSpecList,
                             TurAemSourceContext turAemSourceContext) {
        if (!DELIVERED || aemObject.isDelivered()) {
            final Locale locale = TurAemCommonsUtils.getLocaleFromAemObject(turAemSourceContext, aemObject);
            if (objectNeedBeIndexed(aemObject, turAemSourceContext)) {
                sendIndex(aemObject, turAemModel, turSNAttributeSpecList, turAemSourceContext, locale);
            } else {
                if (objectNeedBeReIndexed(aemObject, turAemSourceContext)) {
                    sendReindex(aemObject, turAemModel, turSNAttributeSpecList, turAemSourceContext, locale);
                }
                if (!DRY_RUN) {
                    updateIndexingStatus(aemObject, locale, turAemSourceContext);
                }
            }
        } else {
            log.info("Unpublished {} object ({}) deltaId = {}",
                    aemObject.getPath(), turAemSourceContext.getId(), deltaId);
        }
    }

    private void sendReindex(TurAemObject aemObject, TurAemModel turAemModel,
                             List<TurSNAttributeSpec> turSNAttributeSpecList,
                             TurAemSourceContext turAemSourceContext, Locale locale) {
        if (!DRY_RUN) {
            turAemIndexingRepository.findByAemIdAndIndexGroup(aemObject.getPath(), turAemSourceContext.getId())
                    .ifPresent(turAemIndexingsList ->
                            log.info("ReIndexed {} object ({}) from {} to {} and deltaId = {}",
                                    aemObject.getPath(), turAemSourceContext.getId(),
                                    turAemIndexingsList.getFirst().getDate(),
                                    TurAemCommonsUtils.getDeltaDate(aemObject, turAemSourceContext,
                                            turAemContentDefinitionProcess), deltaId));
        }
        sendToTuringToBeIndexed(aemObject, turAemModel, turSNAttributeSpecList, locale,
                turAemSourceContext);
    }

    private void sendIndex(TurAemObject aemObject, TurAemModel turAemModel,
                           List<TurSNAttributeSpec> turSNAttributeSpecList, TurAemSourceContext turAemSourceContext,
                           Locale locale) {
        if (!DRY_RUN) {
            createIndexingStatus(aemObject, locale, turAemSourceContext);
        }
        sendToTuringToBeIndexed(aemObject, turAemModel, turSNAttributeSpecList, locale,
                turAemSourceContext);
    }

    private void createIndexingStatus(TurAemObject aemObject, Locale locale, TurAemSourceContext turAemSourceContext) {
        turAemIndexingRepository.save(createTurAemIndexing(aemObject, locale, turAemSourceContext));
        log.info("Created status: {} object ({}) and deltaId = {}", aemObject.getPath(),
                turAemSourceContext.getId(), deltaId);
    }

    private void updateIndexingStatus(TurAemObject aemObject, Locale locale, TurAemSourceContext turAemSourceContext) {
        turAemIndexingRepository.findByAemIdAndIndexGroup(aemObject.getPath(), turAemSourceContext.getId())
                .filter(turAemIndexingList -> !turAemIndexingList.isEmpty())
                .ifPresent(turAemIndexingList -> {
                    if (turAemIndexingList.size() > 1) {
                        turAemIndexingRepository.deleteByAemIdAndIndexGroup(aemObject.getPath(),
                                turAemSourceContext.getId());
                        log.info("Removed duplicated status {} object ({})",
                                aemObject.getPath(), turAemSourceContext.getId());
                        turAemIndexingRepository.save(createTurAemIndexing(aemObject, locale, turAemSourceContext));
                        log.info("Recreated status {} object ({}) and deltaId = {}",
                                aemObject.getPath(), turAemSourceContext.getId(), deltaId);
                    } else {
                        turAemIndexingRepository.save(turAemIndexingList.getFirst()
                                .setDate(TurAemCommonsUtils.getDeltaDate(aemObject, turAemSourceContext,
                                        turAemContentDefinitionProcess))
                                .setDeltaId(deltaId));
                        log.info("Updated status {} object ({}) deltaId = {}",
                                aemObject.getPath(), turAemSourceContext.getId(), deltaId);
                    }
                });
    }

    private TurAemPluginIndexing createTurAemIndexing(TurAemObject aemObject, Locale locale,
                                                      TurAemSourceContext turAemSourceContext) {
        return new TurAemPluginIndexing()
                .setAemId(aemObject.getPath())
                .setIndexGroup(turAemSourceContext.getId())
                .setDate(TurAemCommonsUtils.getDeltaDate(aemObject, turAemSourceContext,
                        turAemContentDefinitionProcess))
                .setDeltaId(deltaId)
                .setOnce(TurAemCommonsUtils.isOnceConfig(aemObject.getPath(), config))
                .setLocale(locale);
    }

    private void sendToTuringToBeIndexed(TurAemObject aemObject, TurAemModel turAemModel,
                                         List<TurSNAttributeSpec> turSNAttributeSpecList, Locale locale,
                                         TurAemSourceContext turAemSourceContext) {
        TurAemAttrProcess turAEMAttrProcess = new TurAemAttrProcess();
        TurAemTargetAttrValueMap turAemTargetAttrValueMap = turAEMAttrProcess
                .prepareAttributeDefs(aemObject, turAemContentDefinitionProcess, turSNAttributeSpecList,
                        turAemSourceContext);
        turAemTargetAttrValueMap.merge(TurAemCommonsUtils.runCustomClassFromContentType(turAemModel,
                aemObject, turAemSourceContext));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(SITE, siteName);
        turAemTargetAttrValueMap.entrySet().stream()
                .filter(entry -> !CollectionUtils.isEmpty(entry.getValue()))
                .forEach(entry -> {
                    String attributeName = entry.getKey();
                    entry.getValue().forEach(attributeValue -> {
                        if (!StringUtils.isBlank(attributeValue)) {
                            if (attributes.containsKey(attributeName)) {
                                TurAemCommonsUtils.addItemInExistingAttribute(attributeValue, attributes, attributeName);
                            } else {
                                TurAemCommonsUtils.addFirstItemToAttribute(attributeName, attributeValue, attributes);
                            }
                        }
                    });
                });
        turConnectorContext.addJobItem(new TurSNJobItem(TurSNJobAction.CREATE,
                Collections.singletonList(config.getDefaultSNSiteConfig().getName()),
                locale,
                attributes,
                TurAemCommonsUtils.castSpecToJobSpec(
                        TurAemCommonsUtils.getDefinitionFromModel(turSNAttributeSpecList, attributes))
        ));
    }
}
