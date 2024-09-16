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

package com.viglet.turing.connector.aem;

import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.TurSNConstants;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.*;
import com.viglet.turing.commons.exception.TurRuntimeException;
import com.viglet.turing.connector.aem.commons.TurAemAttrProcess;
import com.viglet.turing.connector.aem.commons.TurAemCommonsUtils;
import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemLocalePathContext;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.persistence.model.*;
import com.viglet.turing.connector.aem.persistence.repository.*;
import com.viglet.turing.connector.cms.beans.TurCmsContext;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueMap;
import com.viglet.turing.connector.cms.mappers.TurCmsModel;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;


/**
 * @author Alexandre Oliveira
 * @since 0.3.9
 */
@Slf4j
@Getter
@Component
public class TurAemIndexerTool {
    public static final String JCR_PRIMARY_TYPE = "jcr:primaryType";
    public static final String CONTENT_FRAGMENT = "content-fragment";
    public static final String STATIC_FILE = "static-file";
    public static final String SITE = "site";
    public static final String DATA_MASTER = "data/master";
    public static final String METADATA = "metadata";
    public static final String JCR = "jcr:";
    public static final String STATIC_FILE_SUB_TYPE = "STATIC_FILE";
    public static final String ONCE = "once";
    public static final String ID_ATTR = "id";
    private static final String CQ_PAGE = "cq:Page";
    private static final String DAM_ASSET = "dam:Asset";
    public static final String CQ = "cq:";
    private final String deltaId = UUID.randomUUID().toString();
    private final Set<String> visitedLinks = new HashSet<>();
    private final Queue<String> remainingLinks = new LinkedList<>();
    private final String turingUrl;
    private final String turingApiKey;
    private final int timeout;
    private final int jobSize;
    private final TurAemAttributeSpecificationRepository turAemAttributeSpecificationRepository;
    private String siteName;
    private TurSNJobItems turSNJobItems = new TurSNJobItems();
    public static final String FIRST_TIME = "FIRST_TIME";
    public static final String REP = "rep:";
    private final TurAemIndexingRepository turAemIndexingRepository;
    private final TurAemSystemRepository turAemSystemRepository;
    private final TurAemConfigVarRepository turAemConfigVarRepository;
    private final TurAemSourceLocalePathRepository turAemSourceLocalePathRepository;
    private final TurAemModelRepository turAemModelRepository;
    private final TurAemSourceRepository turAemSourceRepository;

    @Inject
    public TurAemIndexerTool(@Value("${turing.url}") String turingUrl,
                             @Value("${turing.apiKey}") String turingApiKey,
                             @Value("${turing.aem.timeout:5000}") int timeout,
                             @Value("${turing.aem.job.size:50}") int jobSize,
                             @Value("${turing.aem.show-output:false}") boolean showOutput,
                             TurAemIndexingRepository turAemIndexingRepository,
                             TurAemSystemRepository turAemSystemRepository,
                             TurAemConfigVarRepository turAemConfigVarRepository,
                             TurAemSourceLocalePathRepository turAemSourceLocalePathRepository,
                             TurAemModelRepository turAemModelRepository, TurAemSourceRepository turAemSourceRepository,
                             TurAemAttributeSpecificationRepository turAemAttributeSpecificationRepository) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
        this.timeout = timeout;
        this.jobSize = jobSize;
        this.turAemIndexingRepository = turAemIndexingRepository;
        this.turAemSystemRepository = turAemSystemRepository;
        this.turAemConfigVarRepository = turAemConfigVarRepository;
        this.turAemSourceLocalePathRepository = turAemSourceLocalePathRepository;
        this.turAemModelRepository = turAemModelRepository;
        this.turAemSourceRepository = turAemSourceRepository;
        this.turAemAttributeSpecificationRepository = turAemAttributeSpecificationRepository;
    }

    public TurAemSourceContext getTurAemSourceContext(TurAemSource turAemSource) {
        Collection<TurAemLocalePathContext> turAemLocalePathContexts = new HashSet<>();
        turAemSourceLocalePathRepository.findByTurAemSource(turAemSource).ifPresent(turAemSourceLocalePaths ->
                turAemSourceLocalePaths.forEach(localePath -> turAemLocalePathContexts
                        .add(TurAemLocalePathContext.builder()
                                .locale(localePath.getLocale())
                                .path(localePath.getPath())
                                .build())));
        return TurAemSourceContext.builder()
                .id(turAemSource.getId())
                .contentType(turAemSource.getContentType())
                .rootPath(turAemSource.getRootPath())
                .url(turAemSource.getUrl())
                .oncePattern(turAemSource.getOncePattern())
                .password(turAemSource.getPassword())
                .urlPrefix(turAemSource.getUrlPrefix())
                .username(turAemSource.getUsername())
                .localePaths(turAemLocalePathContexts)
                .build();
    }

    public void run(TurAemSource turAemSource) {
        TurAemSourceContext turAemSourceContext = getTurAemSourceContext(turAemSource);
        if (turAemConfigVarRepository.findById(FIRST_TIME).isEmpty()) {
            log.info("This is the first time, waiting next schedule.");
        } else {
            log.info("Starting indexing");
            getNodesFromJson(turAemSourceContext);
            deIndexObject(turAemSourceContext);
            updateSystemOnce(turAemSourceContext);
            clearQueue();
        }
    }

    private void getNodesFromJson(TurAemSourceContext turAemSourceContext) {
        if (usingContentTypeParameter(turAemSourceContext)) {

            turAemModelRepository.findByTurAemSourceAndType(getTurAemSource(turAemSourceContext),
                            getContentType(turAemSourceContext))
                    .ifPresentOrElse(cmsModel -> jsonByContentType(turAemSourceContext),
                            () -> log.error("{} type is not configured in CTD Mapping XML file.",
                                    getContentType(turAemSourceContext)));
        }
    }

    private void jsonByContentType(TurAemSourceContext turAemSourceContext) {
        TurAemCommonsUtils.getInfinityJson(getRootPath(turAemSourceContext), turAemSourceContext, false)
                .ifPresent(infinityJson -> {
                    TurAemCommonsUtils.getSiteName(infinityJson).ifPresentOrElse(s -> this.siteName = s,
                            () -> log.error("No site name the {} root path ({})",
                                    getRootPath(turAemSourceContext),
                                    turAemSourceContext.getId()));
                    log.info("Site Name: {}", siteName);
                    addItemToQueue(getRootPath(turAemSourceContext));
                    processQueue(turAemSourceContext);
                });

    }

    private void clearQueue() {
        visitedLinks.clear();
        remainingLinks.clear();
    }

    private void updateSystemOnce(TurAemSourceContext turAemSourceContext) {
        turAemSystemRepository.findByConfig(configOnce(turAemSourceContext))
                .ifPresentOrElse(turAemSystem -> {
                            turAemSystem.setBooleanValue(true);
                            turAemSystemRepository.save(turAemSystem);
                        },
                        () -> turAemSystemRepository.save(new TurAemSystem(configOnce(turAemSourceContext), true)));
    }

    private String configOnce(TurAemSourceContext turAemSourceContext) {
        return turAemSourceContext.getId() + "/" + ONCE;
    }

    private String getContentType(TurAemSourceContext turAemSourceContext) {
        return turAemSourceContext.getContentType();
    }

    private boolean usingContentTypeParameter(TurAemSourceContext turAemSourceContext) {
        return StringUtils.isNotBlank(getContentType(turAemSourceContext));
    }

    private String getRootPath(TurAemSourceContext turAemSourceContext) {
        return turAemSourceContext.getRootPath();
    }

    private void processQueue(TurAemSourceContext turAemSourceContext) {
        while (!remainingLinks.isEmpty()) {
            String url = remainingLinks.poll();
            TurAemCommonsUtils.getInfinityJson(url, turAemSourceContext, false)
                    .ifPresent(infinityJson -> {
                        TurAemSource turAemSource = getTurAemSource(turAemSourceContext);
                        turAemModelRepository.findByTurAemSourceAndType(turAemSource, getContentType(turAemSourceContext))
                                .ifPresent(model -> {
                                    addTurSNJobItemByType(new TurAemObject(url, infinityJson),
                                            new ArrayList<>(turAemSource.getAttributeSpecifications()),
                                            turAemSourceContext);
                                });
                        sendToTuringWhenMaxSize(turAemSourceContext);
                        getInfoQueue();
                        if (infinityJson.has(JCR_PRIMARY_TYPE)
                                && infinityJson.getString(JCR_PRIMARY_TYPE).equals(getContentType(turAemSourceContext))) {
                            getNodeFromJson(turAemSourceContext, url, infinityJson, turAemSourceContext.getSubType());
                        }
                    });
        }
        sendToTuring(turAemSourceContext);
    }

    private @NotNull TurAemSource getTurAemSource(TurAemSourceContext turAemSourceContext) {
        return turAemSourceRepository
                .getReferenceById(turAemSourceContext.getId());
    }


    private void addItemToQueue(String path) {
        if (visitedLinks.add(path) && !remainingLinks.offer(path)) {
            log.error("Item didn't add to queue: {}", path);
        }
    }

    private void getNodeFromJson(TurAemSourceContext turAemSourceContext, String url, JSONObject jsonObject,
                                 String subType) {
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith(JCR) && !key.startsWith(REP) && !key.startsWith(CQ)
                    && (subType.equals(STATIC_FILE_SUB_TYPE)
                    || TurAemCommonsUtils.checkIfFileHasNotImageExtension(key))) {
                String urlChild = url + "/" + key;
                if (!isOnce(turAemSourceContext) || !isOnceConfig(turAemSourceContext, urlChild)) {
                    addItemToQueue(urlChild);
                } else if (isOnceConfig(turAemSourceContext, urlChild)) {
                    log.info("Ignored Url by Once: {}", urlChild);
                }
            }
        });
    }

    private boolean isOnce(TurAemSourceContext turAemSourceContext) {
        return turAemSystemRepository.findByConfig(configOnce(turAemSourceContext))
                .map(TurAemSystem::isBooleanValue).orElse(false);
    }

    private boolean isOnceConfig(TurAemSourceContext turAemSourceContext, String path) {
        String pattern = turAemSourceContext.getOncePattern();
        if (StringUtils.isNotBlank(pattern)) {
            return Pattern.compile(pattern).matcher(path).lookingAt() &&
                    turAemIndexingRepository.findByAemIdAndIndexGroup(path, turAemSourceContext.getId())
                            .isPresent();
        }
        return false;
    }

    private void getInfoQueue() {
        log.info("Total Job Item: {}", Iterators.size(turSNJobItems.iterator()));
        log.info("Total Visited Links: {}", (long) visitedLinks.size());
        log.info("Queue Size: {}", (long) remainingLinks.size());
    }

    private void sendToTuringWhenMaxSize(TurAemSourceContext turAemSourceContext) {
        if (turSNJobItems.size() >= jobSize) {
            sendToTuring(turAemSourceContext);
            turSNJobItems = new TurSNJobItems();
        }
    }

    private void sendToTuring(TurAemSourceContext turAemSourceContext) {
        if (log.isDebugEnabled()) {
            for (TurSNJobItem turSNJobItem : turSNJobItems) {
                log.debug("TurSNJobItem Id: {}", turSNJobItem.getAttributes().get(ID_ATTR));
            }
        }
        try {
            if (!TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(URI.create(turingUrl).toURL(), turAemSourceContext.getTurSNSite(),
                            new TurApiKeyCredentials(turingApiKey)),
                    false)) {
                throw new TurRuntimeException("Import Job Failed");
            }
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }

    }

    private void addTurSNJobItemByType(TurAemObject aemObject,
                                       List<TurSNAttributeSpec> targetAttrDefinitions,
                                       TurAemSourceContext turAemSourceContext) {
        String contentType = Objects.requireNonNull(getContentType(turAemSourceContext));
        if (contentType.equals(CQ_PAGE)) {
            addTurSNJobItemToIndex(aemObject, targetAttrDefinitions, turAemSourceContext);
        } else if (contentType.equals(DAM_ASSET) && !StringUtils.isEmpty(turAemSourceContext.getSubType())) {
            if (turAemSourceContext.getSubType().equals(CONTENT_FRAGMENT) && aemObject.isContentFragment()) {
                aemObject.setDataPath(DATA_MASTER);
                addTurSNJobItemToIndex(aemObject, targetAttrDefinitions, turAemSourceContext);
            } else if (turAemSourceContext.getSubType().equals(STATIC_FILE)) {
                aemObject.setDataPath(METADATA);
                addTurSNJobItemToIndex(aemObject, targetAttrDefinitions, turAemSourceContext);
            }
        }
    }

    private void addTurSNJobItemToIndex(TurAemObject aemObject,
                                        List<TurSNAttributeSpec> turSNAttributeSpecList,
                                        TurAemSourceContext turAemSourceContext) {
        final Locale locale = TurAemCommonsUtils.getLocaleFromAemObject(turAemSourceContext, aemObject);
        if (objectNeedBeIndexed(aemObject, turAemSourceContext)) {
            createIndexingStatus(aemObject, locale, turAemSourceContext);
            sendToTuringToBeIndexed(aemObject, turSNAttributeSpecList, locale, turAemSourceContext);
        } else {
            if (objectNeedBeReIndexed(aemObject, turAemSourceContext)) {
                turAemIndexingRepository.findByAemIdAndIndexGroup(aemObject.getPath(), turAemSourceContext.getId())
                        .ifPresent(turAemIndexingsList ->
                                log.info("ReIndexed {} object ({}) from {} to {} and deltaId = {}",
                                        aemObject.getPath(), turAemSourceContext.getId(), turAemIndexingsList.getFirst().getDate(),
                                        TurAemCommonsUtils.getDeltaDate(aemObject), deltaId));
                sendToTuringToBeIndexed(aemObject, turSNAttributeSpecList, locale, turAemSourceContext);
            }
            updateIndexingStatus(aemObject, locale, turAemSourceContext);
        }
    }

    public TurCmsTargetAttrValueMap prepareAttributeDefs(TurAemObject aemObject,
                                                         List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                         TurAemSourceContext turAemSourceContext) {
        TurAemAttrProcess turAemAttrProcess = new TurAemAttrProcess();
        return turAemModelRepository.findByTurAemSourceAndType(getTurAemSource(turAemSourceContext),
                        aemObject.getType())
                .map(turCmsModel -> {
                    TurCmsContext context = new TurCmsContext(aemObject);
                    TurCmsTargetAttrValueMap turCmsTargetAttrValueMap = new TurCmsTargetAttrValueMap();
                    turCmsModel.getTargetAttrs().stream().filter(Objects::nonNull)
                            .forEach(targetAttr -> {
                                TurCmsTargetAttr turCmsTargetAttr = new TurCmsTargetAttr();
                                turCmsTargetAttr.setType(null);
                                turCmsTargetAttr.setSourceAttrs(null);
                                turCmsTargetAttr.setName(targetAttr.getName());
                                turCmsTargetAttr.setTextValue(null);
                                turCmsTargetAttr.setMultiValued(false);
                                turCmsTargetAttr.setFacetName(null);
                                turCmsTargetAttr.setDescription(null);
                                turCmsTargetAttr.setClassName(null);
                                turCmsTargetAttr.setMandatory(false);

                                log.debug("TargetAttr: {}", targetAttr);
                                context.setTurCmsTargetAttr(targetAttr);
                                if (hasCustomClass(targetAttr)) {
                                    turCmsTargetAttrValueMap.merge(turAemAttrProcess.process(context, turSNAttributeSpecList,
                                            turAemSourceContext));
                                } else {
                                    targetAttr.getSourceAttrs().stream().filter(Objects::nonNull)
                                            .forEach(sourceAttr ->
                                                    turCmsTargetAttrValueMap.merge(
                                                            turAemAttrProcess.addTargetAttrValuesBySourceAttr(turAemSourceContext,
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
    public static boolean hasCustomClass(TurCmsTargetAttr targetAttr) {
        return targetAttr.getSourceAttrs() == null
                && StringUtils.isNotBlank(targetAttr.getClassName());
    }
    private void sendToTuringToBeIndexed(TurAemObject aemObject,
                                         List<TurSNAttributeSpec> turSNAttributeSpecList,
                                         Locale locale,
                                         TurAemSourceContext turAemSourceContext) {

        TurCmsTargetAttrValueMap turCmsTargetAttrValueMap = prepareAttributeDefs(
                aemObject,
                turSNAttributeSpecList,
                turAemSourceContext
        );
        TurAemSource turAemSource = getTurAemSource(turAemSourceContext);
        turAemModelRepository.findByTurAemSourceAndType(turAemSource,
                turAemSourceContext.getContentType()).ifPresent(model ->
                turCmsTargetAttrValueMap.merge(TurAemCommonsUtils
                        .runCustomClassFromContentType(
                                getTurCmsModel(turAemSourceContext,
                                        model,
                                        getTurCmsTargetAttrs(model, turAemSource)),
                                aemObject,
                                turAemSourceContext)));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(SITE, siteName);
        turCmsTargetAttrValueMap.entrySet().stream()
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
        addJobItemToItems(new TurSNJobItem(TurSNJobAction.CREATE,
                locale, Collections.singletonList(turAemSourceContext.getTurSNSite()),
                TurAemCommonsUtils.castSpecToJobSpec(
                        TurAemCommonsUtils.getDefinitionFromModel(turSNAttributeSpecList, attributes)),
                attributes));
    }

    private @NotNull List<TurCmsTargetAttr> getTurCmsTargetAttrs(TurAemModel model, TurAemSource turAemSource) {
        List<TurCmsTargetAttr> turCmsTargetAttrs = new ArrayList<>();
        model.getTargetAttrs().forEach(targetAttr ->
                turAemAttributeSpecificationRepository
                        .findByTurAemSourceAndName(turAemSource, targetAttr.getName())
                        .ifPresent(spec ->
                                turCmsTargetAttrs.add(getTurCmsTargetAttr(spec,
                                        getTurCmsSourceAttrs(targetAttr)))));
        return turCmsTargetAttrs;
    }

    private static TurCmsModel getTurCmsModel(TurAemSourceContext turAemSourceContext, TurAemModel model,
                                              List<TurCmsTargetAttr> turCmsTargetAttrs) {
        return TurCmsModel.builder()
                .type(turAemSourceContext.getContentType())
                .subType(turAemSourceContext.getSubType())
                .className(model.getClassName())
                .targetAttrs(turCmsTargetAttrs)
                .build();
    }

    private static @NotNull List<TurCmsSourceAttr> getTurCmsSourceAttrs(TurAemTargetAttribute targetAttr) {
        List<TurCmsSourceAttr> turCmsSourceAttrs = new ArrayList<>();
        targetAttr.getSourceAttrs().forEach(aemSource -> {
            TurCmsSourceAttr turCmsSourceAttr = new TurCmsSourceAttr();
            turCmsSourceAttr.setClassName(aemSource.getClassName());
            turCmsSourceAttr.setName(aemSource.getName());
            turCmsSourceAttr.setUniqueValues(false);
            turCmsSourceAttr.setConvertHtmlToText(false);
            turCmsSourceAttrs.add(turCmsSourceAttr);
        });
        return turCmsSourceAttrs;
    }

    private static @NotNull TurCmsTargetAttr getTurCmsTargetAttr(TurAemAttributeSpecification spec, List<TurCmsSourceAttr> turCmsSourceAttrs) {
        TurCmsTargetAttr turCmsTargetAttr = new TurCmsTargetAttr();
        turCmsTargetAttr.setSourceAttrs(turCmsSourceAttrs);
        turCmsTargetAttr.setClassName(spec.getClassName());
        turCmsTargetAttr.setDescription(spec.getDescription());
        turCmsTargetAttr.setFacet(spec.isFacet());
        turCmsTargetAttr.setMandatory(spec.isMandatory());
        turCmsTargetAttr.setName(spec.getName());
        turCmsTargetAttr.setTextValue(spec.getText());
        turCmsTargetAttr.setFacetName(spec.getFacetNames());
        turCmsTargetAttr.setMultiValued(spec.isMultiValued());
        turCmsTargetAttr.setType(spec.getType());
        return turCmsTargetAttr;
    }

    private void deIndexObject(TurAemSourceContext turAemSourceContext) {
        turAemIndexingRepository.findContentsShouldBeDeIndexed(turAemSourceContext.getId(), deltaId)
                .ifPresent(contents -> {
                            contents.forEach(content -> {
                                log.info("DeIndex {} object from {} group and {} delta",
                                        content.getAemId(), turAemSourceContext.getId(), deltaId);
                                Map<String, Object> attributes = new HashMap<>();
                                attributes.put(TurSNConstants.ID_ATTR, content.getAemId());
                                attributes.put(TurSNConstants.SOURCE_APPS_ATTR,
                                        turAemSourceContext.getProviderName());
                                addJobItemToItems(new TurSNJobItem(TurSNJobAction.DELETE,
                                        Collections.singletonList(turAemSourceContext.getTurSNSite()),
                                        content.getLocale(), attributes));
                            });
                            turAemIndexingRepository.deleteContentsWereDeIndexed(turAemSourceContext.getId(), deltaId);
                        }
                );
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
                                .setDate(TurAemCommonsUtils.getDeltaDate(aemObject))
                                .setDeltaId(deltaId));
                        log.info("Updated status {} object ({}) deltaId = {}",
                                aemObject.getPath(), turAemSourceContext.getId(), deltaId);
                    }
                });
    }

    private void createIndexingStatus(TurAemObject aemObject, Locale locale,
                                      TurAemSourceContext turAemSourceContext) {
        turAemIndexingRepository.save(createTurAemIndexing(aemObject, locale, turAemSourceContext));
        log.info("Created status {} object ({})", aemObject.getPath(), turAemSourceContext.getId());
    }

    private TurAemIndexing createTurAemIndexing(TurAemObject aemObject, Locale locale,
                                                TurAemSourceContext turAemSourceContext) {
        return new TurAemIndexing()
                .setAemId(aemObject.getPath())
                .setIndexGroup(turAemSourceContext.getId())
                .setDate(aemObject.getLastModified().getTime())
                .setDeltaId(deltaId)
                .setOnce(isOnceConfig(turAemSourceContext, aemObject.getPath()))
                .setLocale(locale);
    }

    private boolean objectNeedBeIndexed(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return (!StringUtils.isEmpty(aemObject.getPath()) &&
                !turAemIndexingRepository.existsByAemIdAndIndexGroup(aemObject.getPath(), turAemSourceContext.getId()));
    }

    private boolean objectNeedBeReIndexed(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return !StringUtils.isEmpty(aemObject.getPath()) &&
                turAemIndexingRepository.existsByAemIdAndIndexGroupAndDateNot(aemObject.getPath(),
                        turAemSourceContext.getId(), TurAemCommonsUtils.getDeltaDate(aemObject));
    }

    private void addJobItemToItems(TurSNJobItem turSNJobItem) {
        this.turSNJobItems.add(turSNJobItem);
    }

}
