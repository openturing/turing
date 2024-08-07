package com.viglet.turing.connector.aem.indexer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import com.viglet.turing.client.sn.TurSNConstants;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.job.*;
import com.viglet.turing.commons.exception.TurRuntimeException;
import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.TurAEMAttrProcess;
import com.viglet.turing.connector.aem.commons.TurAEMCommonsUtils;
import com.viglet.turing.connector.aem.commons.context.TurAemLocalePathContext;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemIndexing;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSource;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSystem;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemConfigVarRepository;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemIndexingRepository;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemSourceLocalePathRepository;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemSystemRepository;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueMap;
import com.viglet.turing.connector.cms.mappers.TurCmsContentDefinitionProcess;
import com.viglet.turing.connector.cms.mappers.TurCmsModel;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@Slf4j
@Getter
@Component
public class TurAemIndexerTool {
    public static final String JCR_TITLE = "jcr:title";
    public static final String JCR_CONTENT = "jcr:content";
    public static final String JCR_PRIMARY_TYPE = "jcr:primaryType";
    public static final String CONTENT_FRAGMENT = "content-fragment";
    public static final String STATIC_FILE = "static-file";
    public static final String SITE = "site";
    public static final String DATA_MASTER = "data/master";
    public static final String METADATA = "metadata";
    public static final String JCR = "jcr:";
    public static final String ID = "id";
    public static final String STATIC_FILE_SUB_TYPE = "STATIC_FILE";
    public static final String ONCE = "once";
    public static final String ID_ATTR = "id";
    private static final String CQ_PAGE = "cq:Page";
    private static final String DAM_ASSET = "dam:Asset";
    public static final String CQ = "cq:";
    private static AtomicInteger processed = new AtomicInteger(0);
    private static final AtomicInteger currentPage =  new AtomicInteger(0);
    private static long start;
    private final String deltaId = UUID.randomUUID().toString();
    private final Set<String> visitedLinks = new HashSet<>();
    private final Queue<String> remainingLinks = new LinkedList<>();
    private final String turingUrl;
    private final String turingApiKey;
    private final int timeout;
    private final int jobSize;
    private final boolean showOutput;
    private final String subType = "NONE";
    private String siteName;
    private TurCmsContentDefinitionProcess turCmsContentDefinitionProcess;
    private TurSNJobItems turSNJobItems = new TurSNJobItems();
    public static final String FIRST_TIME = "FIRST_TIME";
    public static final String REP = "rep:";
    private final TurAemIndexingRepository turAemIndexingRepository;
    private final TurAemSystemRepository turAemSystemRepository;
    private final TurAemConfigVarRepository turAemConfigVarRepository;
    private final TurAemSourceLocalePathRepository turAemSourceLocalePathRepository;
    private TurAemSourceContext turAemSourceContext;

    @Inject
    public TurAemIndexerTool(@Value("${turing.url}") String turingUrl,
                             @Value("${turing.apiKey}") String turingApiKey,
                             @Value("${turing.aem.timeout:5000}") int timeout,
                             @Value("${turing.aem.job.size:50}") int jobSize,
                             @Value("${turing.aem.show-output:false}") boolean showOutput,
                             TurAemIndexingRepository turAemIndexingRepository,
                             TurAemSystemRepository turAemSystemRepository,
                             TurAemConfigVarRepository turAemConfigVarRepository,
                             TurAemSourceLocalePathRepository turAemSourceLocalePathRepository) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
        this.timeout = timeout;
        this.jobSize = jobSize;
        this.showOutput = showOutput;
        this.turAemIndexingRepository = turAemIndexingRepository;
        this.turAemSystemRepository = turAemSystemRepository;
        this.turAemConfigVarRepository = turAemConfigVarRepository;
        this.turAemSourceLocalePathRepository = turAemSourceLocalePathRepository;
        this.turAemSourceContext = TurAemSourceContext.builder().build();
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
                .group(turAemSource.getGroup())
                .contentType(turAemSource.getContentType())
                .defaultLocale(turAemSource.getDefaultLocale())
                .rootPath(turAemSource.getRootPath())
                .url(turAemSource.getUrl())
                .siteName(turAemSource.getSiteName())
                .subType(turAemSource.getSubType())
                .turSNSite(turAemSource.getTurSNSite())
                .oncePattern(turAemSource.getOncePattern())
                .providerName(turAemSource.getProviderName())
                .password(turAemSource.getPassword())
                .urlPrefix(turAemSource.getUrlPrefix())
                .username(turAemSource.getUsername())
                .localePaths(turAemLocalePathContexts)
                .build();
    }

    public void run(TurAemSource turAemSource) {
        this.turAemSourceContext = getTurAemSourceContext(turAemSource);
        if (turAemConfigVarRepository.findById(FIRST_TIME).isEmpty()) {
            log.info("This is the first time, waiting next schedule.");
        } else {
            log.info("Starting indexing");
            turCmsContentDefinitionProcess = new TurCmsContentDefinitionProcess(turAemSource.getMappingJson());
            getNodesFromJson();
            deIndexObject();
            updateSystemOnce();
            clearQueue();
        }
    }

    private void clearQueue() {
        visitedLinks.clear();
        remainingLinks.clear();
    }

    private void updateSystemOnce() {
        turAemSystemRepository.findByConfig(configOnce()).ifPresentOrElse(turAemSystem -> {
                    turAemSystem.setBooleanValue(true);
                    turAemSystemRepository.save(turAemSystem);
                },
                () -> turAemSystemRepository.save(new TurAemSystem(configOnce(), true)));
    }


    @NotNull
    private String configOnce() {
        return getGroup() + "/" + ONCE;
    }

    @NotNull
    private String getGroup() {
        return this.turAemSourceContext.getGroup();
    }

    private void getNodesFromJson() {
        if (usingContentTypeParameter()) {
            turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(getContentType())
                    .ifPresentOrElse(cmsModel -> jsonByContentType(),
                            () -> log.error("{} type is not configured in CTD Mapping XML file.",
                                    getContentType()));
        }
    }

    @NotNull
    private String getContentType() {
        return this.turAemSourceContext.getContentType();
    }

    private boolean usingContentTypeParameter() {
        return StringUtils.isNotBlank(getContentType());
    }

    @NotNull
    private String getRootPath() {
        return this.turAemSourceContext.getRootPath();
    }

    private void jsonByContentType() {
        TurAEMCommonsUtils.getInfinityJson(getRootPath(), this.turAemSourceContext).ifPresent(jsonObject -> {
            TurAEMCommonsUtils.getSiteName(jsonObject).ifPresentOrElse(s -> this.siteName = s,
                    () -> log.error("No site name the {} root path ({})", getRootPath(), getGroup()));
            log.info("Site Name: {}", siteName);
            addItemToQueue(getRootPath());
            processQueue();
        });

    }

    private void processQueue() {
        while (!remainingLinks.isEmpty()) {
            String url = remainingLinks.poll();
            TurAEMCommonsUtils.getInfinityJson(url, this.turAemSourceContext).ifPresent(jsonObject -> {
                turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(getContentType()).ifPresent(model ->
                        addTurSNJobItemByType(model, new AemObject(url, jsonObject),
                                turCmsContentDefinitionProcess.getTargetAttrDefinitions()));
                sendToTuringWhenMaxSize();
                getInfoQueue();
                if (jsonObject.has(JCR_PRIMARY_TYPE)
                        && jsonObject.getString(JCR_PRIMARY_TYPE).equals(getContentType())) {
                    getNodeFromJson(url, jsonObject);
                }
            });
        }
        sendToTuring();
    }

    private void addItemToQueue(String path) {
        if (visitedLinks.add(path) && !remainingLinks.offer(path)) {
            log.error("Item didn't add to queue: {}", path);
        }
    }

    private void getNodeFromJson(String url, JSONObject jsonObject) {
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith(JCR) && !key.startsWith(REP) && !key.startsWith(CQ)
                    && (getSubType().equals(STATIC_FILE_SUB_TYPE)
                    || TurAEMCommonsUtils.checkIfFileHasNotImageExtension(key))) {
                String urlChild = url + "/" + key;
                if (!isOnce() || !isOnceConfig(urlChild)) {
                    addItemToQueue(urlChild);
                } else if (isOnceConfig(urlChild)) {
                    log.info("Ignored Url by Once: {}", urlChild);
                }
            }
        });
    }

    private boolean isOnce() {
        return turAemSystemRepository.findByConfig(configOnce())
                .map(TurAemSystem::isBooleanValue).orElse(false);
    }

    private boolean isOnceConfig(String path) {
        String pattern = this.turAemSourceContext.getOncePattern();
        if (StringUtils.isNotBlank(pattern)) {
            return Pattern.compile(pattern).matcher(path).lookingAt() &&
                    turAemIndexingRepository.findByAemIdAndIndexGroup(path, getGroup()).isPresent();
        }
        return false;
    }

    private void getInfoQueue() {
        log.info("Total Job Item: {}", Iterators.size(turSNJobItems.iterator()));
        log.info("Total Visited Links: {}", (long) visitedLinks.size());
        log.info("Queue Size: {}", (long) remainingLinks.size());
    }

    private void sendToTuringWhenMaxSize() {
        if (turSNJobItems.size() >= jobSize) {
            sendToTuring();
            turSNJobItems = new TurSNJobItems();
        }
    }

    private void sendToTuring() {
        if (log.isDebugEnabled()) {
            for (TurSNJobItem turSNJobItem : turSNJobItems) {
                log.debug("TurSNJobItem Id: {}", turSNJobItem.getAttributes().get(ID_ATTR));
            }
        }
        if (this.showOutput) {
            showOutput(turSNJobItems);
        }
        try {
            if (!TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(URI.create(turingUrl).toURL(), this.turAemSourceContext.getTurSNSite(),
                            new TurApiKeyCredentials(turingApiKey)),
                    false)) {
                throw new TurRuntimeException("Import Job Failed");
            }
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }

    }

    private void addTurSNJobItemByType(TurCmsModel turCmsModel, AemObject aemObject,
                                       List<TurSNAttributeSpec> targetAttrDefinitions) {
        String contentType = Objects.requireNonNull(getContentType());
        if (contentType.equals(CQ_PAGE)) {
            addTurSNJobItemToIndex(aemObject, turCmsModel, targetAttrDefinitions);
        } else if (contentType.equals(DAM_ASSET) && !StringUtils.isEmpty(turCmsModel.getSubType())) {
            if (turCmsModel.getSubType().equals(CONTENT_FRAGMENT) && aemObject.isContentFragment()) {
                aemObject.setDataPath(DATA_MASTER);
                addTurSNJobItemToIndex(aemObject, turCmsModel, targetAttrDefinitions);
            } else if (turCmsModel.getSubType().equals(STATIC_FILE)) {
                aemObject.setDataPath(METADATA);
                addTurSNJobItemToIndex(aemObject, turCmsModel, targetAttrDefinitions);
            }
        }
    }

    private void itemsProcessedStatus() {
        if (processed.get() == 0) {
            currentPage.incrementAndGet();
        }
        if (processed.get() >= jobSize) {
            processed = new AtomicInteger(0);
            start = System.currentTimeMillis();
        } else {
            processed.incrementAndGet();
        }
    }

    private void deIndexObject() {
        turAemIndexingRepository.findContentsShouldBeDeIndexed(getGroup(), deltaId).ifPresent(contents -> {
                    contents.forEach(content -> {
                        log.info("DeIndex {} object from {} group and {} delta",
                                content.getAemId(), getGroup(), deltaId);
                        Map<String, Object> attributes = new HashMap<>();
                        attributes.put(TurSNConstants.ID_ATTR, content.getAemId());
                        attributes.put(TurSNConstants.SOURCE_APPS_ATTR,
                                this.turAemSourceContext.getProviderName());
                        addJobItemToItems(new TurSNJobItem(TurSNJobAction.DELETE,
                                Collections.singletonList(this.turAemSourceContext.getTurSNSite()),
                                content.getLocale(), attributes));
                    });
                    turAemIndexingRepository.deleteContentsWereDeIndexed(getGroup(), deltaId);
                }
        );
    }

    private void addTurSNJobItemToIndex(AemObject aemObject, TurCmsModel turCmsModel,
                                        List<TurSNAttributeSpec> turSNAttributeSpecList) {
        itemsProcessedStatus();
        if (isNotDryRun()) {
            final Locale locale = TurAEMCommonsUtils.getLocaleFromAemObject(this.turAemSourceContext, aemObject);
            if (objectNeedBeIndexed(aemObject)) {
                createIndexingStatus(aemObject, locale);
                sendToTuringToBeIndexed(aemObject, turCmsModel, turSNAttributeSpecList, locale);
            } else {
                if (objectNeedBeReIndexed(aemObject)) {
                    turAemIndexingRepository.findByAemIdAndIndexGroup(aemObject.getPath(), getGroup())
                            .ifPresent(turAemIndexingsList ->
                                    log.info("ReIndexed {} object ({}) from {} to {} and deltaId = {}",
                                            aemObject.getPath(), getGroup(), turAemIndexingsList.getFirst().getDate(),
                                            TurAEMCommonsUtils.getDeltaDate(aemObject), deltaId));
                    sendToTuringToBeIndexed(aemObject, turCmsModel, turSNAttributeSpecList, locale);
                }
                updateIndexingStatus(aemObject, locale);
            }

        }
    }

    private void sendToTuringToBeIndexed(AemObject aemObject, TurCmsModel turCmsModel,
                                         List<TurSNAttributeSpec> turSNAttributeSpecList, Locale locale) {
        TurAEMAttrProcess turAEMAttrProcess = new TurAEMAttrProcess();
        TurCmsTargetAttrValueMap turCmsTargetAttrValueMap = turAEMAttrProcess
                .prepareAttributeDefs(aemObject, turCmsContentDefinitionProcess, turSNAttributeSpecList,
                        this.turAemSourceContext);
        turCmsTargetAttrValueMap.merge(TurAEMCommonsUtils.runCustomClassFromContentType(turCmsModel, aemObject,
                this.turAemSourceContext));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(SITE, siteName);
        turCmsTargetAttrValueMap.entrySet().stream()
                .filter(entry -> !CollectionUtils.isEmpty(entry.getValue()))
                .forEach(entry -> {
                    String attributeName = entry.getKey();
                    entry.getValue().forEach(attributeValue -> {
                        if (!StringUtils.isBlank(attributeValue)) {
                            if (attributes.containsKey(attributeName)) {
                                TurAEMCommonsUtils.addItemInExistingAttribute(attributeValue, attributes, attributeName);
                            } else {
                                TurAEMCommonsUtils.addFirstItemToAttribute(attributeName, attributeValue, attributes);
                            }
                        }
                    });
                });
        addJobItemToItems(new TurSNJobItem(TurSNJobAction.CREATE,
                locale, Collections.singletonList(this.turAemSourceContext.getTurSNSite()), TurAEMCommonsUtils.castSpecToJobSpec(
                TurAEMCommonsUtils.getDefinitionFromModel(turSNAttributeSpecList, attributes)),
                attributes));
    }

    private void updateIndexingStatus(AemObject aemObject, Locale locale) {
        turAemIndexingRepository.findByAemIdAndIndexGroup(aemObject.getPath(), getGroup())
                .filter(turAemIndexingList -> !turAemIndexingList.isEmpty())
                .ifPresent(turAemIndexingList -> {
                    if (turAemIndexingList.size() > 1) {
                        turAemIndexingRepository.deleteByAemIdAndIndexGroup(aemObject.getPath(), getGroup());
                        log.info("Removed duplicated status {} object ({})",
                                aemObject.getPath(), getGroup());
                        turAemIndexingRepository.save(createTurAemIndexing(aemObject, locale));
                        log.info("Recreated status {} object ({}) and deltaId = {}",
                                aemObject.getPath(), getGroup(), deltaId);
                    } else {
                        turAemIndexingRepository.save(turAemIndexingList.getFirst()
                                .setDate(TurAEMCommonsUtils.getDeltaDate(aemObject))
                                .setDeltaId(deltaId));
                        log.info("Updated status {} object ({}) deltaId = {}",
                                aemObject.getPath(), getGroup(), deltaId);
                    }
                });
    }

    private void createIndexingStatus(AemObject aemObject, Locale locale) {
        turAemIndexingRepository.save(createTurAemIndexing(aemObject, locale));
        log.info("Created status {} object ({})", aemObject.getPath(), getGroup());
    }

    private TurAemIndexing createTurAemIndexing(AemObject aemObject, Locale locale) {
        return new TurAemIndexing()
                .setAemId(aemObject.getPath())
                .setIndexGroup(getGroup())
                .setDate(aemObject.getLastModified().getTime())
                .setDeltaId(deltaId)
                .setOnce(isOnceConfig(aemObject.getPath()))
                .setLocale(locale);
    }

    private boolean objectNeedBeIndexed(AemObject aemObject) {
        return (!StringUtils.isEmpty(aemObject.getPath()) &&
                !turAemIndexingRepository.existsByAemIdAndIndexGroup(aemObject.getPath(), getGroup()));
    }

    private boolean objectNeedBeReIndexed(AemObject aemObject) {
        return !StringUtils.isEmpty(aemObject.getPath()) &&
                turAemIndexingRepository.existsByAemIdAndIndexGroupAndDateNot(aemObject.getPath(),
                        getGroup(), TurAEMCommonsUtils.getDeltaDate(aemObject));
    }

    private void addJobItemToItems(TurSNJobItem turSNJobItem) {
        this.turSNJobItems.add(turSNJobItem);
    }

    private boolean isNotDryRun() {
        return true;
    }

    private void showOutput(TurSNJobItems turSNJobItems) {
        if (log.isDebugEnabled()) try {
            log.debug(new ObjectMapper().writeValueAsString(turSNJobItems));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }
    }


}
