package com.viglet.turing.connector.aem.indexer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.job.*;
import com.viglet.turing.connector.aem.indexer.ext.ExtContentInterface;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemIndexing;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSource;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSystem;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemConfigVarRepository;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemIndexingRepository;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemSourceLocalePathRepository;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemSystemRepository;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueList;
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

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private static int processed = 0;
    private static int currentPage = 0;
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
    private TurAemSource turAemSource;
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
        this.turAemSource = TurAemSource.builder().build();
    }

    public static boolean checkIfFileHasImageExtension(String s) {
        String[] imageExtensions = {".jpg", ".png", ".jpeg", ".svg", ".webp"};
        return Arrays.stream(imageExtensions).anyMatch(suffix -> s.toLowerCase().endsWith(suffix));
    }

    @NotNull
    private static List<TurSNJobAttributeSpec> castSpecToJobSpec(List<TurSNAttributeSpec> turSNAttributeSpecList) {
        return turSNAttributeSpecList.stream()
                .filter(Objects::nonNull)
                .map(TurSNJobAttributeSpec.class::cast)
                .collect(Collectors.toList());
    }

    private static void addItemInExistingAttribute(String attributeValue,
                                                   Map<String, Object> attributes,
                                                   String attributeName) {
        if (attributes.get(attributeName) instanceof ArrayList)
            addItemToArray(attributes, attributeName, attributeValue);
        else convertAttributeSingleValueToArray(attributes, attributeName, attributeValue);
    }

    private static void convertAttributeSingleValueToArray(Map<String, Object> attributes,
                                                           String attributeName, String attributeValue) {
        List<Object> attributeValues = new ArrayList<>();
        attributeValues.add(attributes.get(attributeName));
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);
    }

    private static void addItemToArray(Map<String, Object> attributes, String attributeName, String attributeValue) {
        List<String> attributeValues = new ArrayList<>(((List<?>) attributes.get(attributeName))
                .stream().map(String.class::cast).toList());
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);

    }

    public void run(TurAemSource turAemSource) {
        this.turAemSource = turAemSource;
        turAemSourceLocalePathRepository.findByTurAemSource(turAemSource).ifPresent(turAemSourceLocalePaths ->
                this.turAemSource.setLocalePaths(turAemSourceLocalePaths));

        if (turAemConfigVarRepository.findById(FIRST_TIME).isEmpty()) {
            log.info("This is the first time, waiting next schedule.");
        } else {
            log.info("Starting indexing");
            turCmsContentDefinitionProcess = new TurCmsContentDefinitionProcess(this.turAemSource.getMappingJson());
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
        return this.turAemSource.getGroup();
    }

    private void getNodesFromJson() {
        if (usingContentTypeParameter()) {
            turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(getContentType())
                    .ifPresentOrElse(cmsModel -> jsonByContentType(),
                            () -> log.error(String.format("%s type is not configured in CTD Mapping XML file.",
                                    getContentType())));
        }
    }

    @NotNull
    private String getContentType() {
        return this.turAemSource.getContentType();
    }

    private boolean usingContentTypeParameter() {
        return StringUtils.isNotBlank(getContentType());
    }

    private TurAemContext getTurAemContext() {
        return TurAemContext.builder()
                .url( this.turAemSource.getUrl())
                .password(this.turAemSource.getPassword())
                .username(this.turAemSource.getUsername())
                .source(this.turAemSource)
                .build();
    }

    @NotNull
    private String getRootPath() {
        return this.turAemSource.getRootPath();
    }

    private void jsonByContentType() {
        JSONObject jsonSite = TurAemUtils.getInfinityJson(getRootPath(), getTurAemContext());
        setSiteName(getRootPath(), jsonSite);
        log.info("Site Name: {}", siteName);
        addItemToQueue(getRootPath());
        processQueue();
    }

    private void processQueue() {
        while (!remainingLinks.isEmpty()) {
            String url = remainingLinks.poll();
            JSONObject jsonObject = TurAemUtils.getInfinityJson(url, getTurAemContext());
            turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(getContentType()).ifPresent(model ->
                    addTurSNJobItemByType(model, new AemObject(url, jsonObject),
                            turCmsContentDefinitionProcess.getTargetAttrDefinitions()));
            sendToTuringWhenMaxSize();
            getInfoQueue();
            if (jsonObject.has(JCR_PRIMARY_TYPE)
                    && jsonObject.getString(JCR_PRIMARY_TYPE).equals(getContentType())) {
                getNodeFromJson(url, jsonObject);
            }
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
            if (!key.startsWith(JCR) && !key.startsWith(REP) && (getSubType().equals(STATIC_FILE_SUB_TYPE)
                    || !checkIfFileHasImageExtension(key))) {
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
        String pattern = this.turAemSource.getOncePattern();
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
        if (this.showOutput){
            showOutput(turSNJobItems);
        }
        try {
            TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(URI.create(turingUrl).toURL(), this.turAemSource.getTurSNSite(),
                            new TurApiKeyCredentials(turingApiKey)),
                    false);
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }

    }

    private void setSiteName(String rootPath, JSONObject jsonSite) {
        if (jsonSite.has(JCR_CONTENT) && jsonSite.getJSONObject(JCR_CONTENT).has(JCR_TITLE)) {
            siteName = jsonSite.getJSONObject(JCR_CONTENT).getString(JCR_TITLE);
        } else {
            log.error(String.format("No site name the %s root path (%s)", rootPath, getGroup()));
        }
    }

    private List<TurSNAttributeSpec> getDefinitionFromModel(List<TurSNAttributeSpec> turSNAttributeSpecList,
                                                            Map<String, Object> targetAttrMap) {
        List<TurSNAttributeSpec> turSNAttributeSpecFromModelList = new ArrayList<>();
        targetAttrMap.forEach((key, value) -> turSNAttributeSpecList.stream()
                .filter(turSNAttributeSpec -> turSNAttributeSpec.getName().equals(key))
                .findFirst().ifPresent(turSNAttributeSpecFromModelList::add));
        return turSNAttributeSpecFromModelList;
    }

    private void addTurSNJobItemByType(TurCmsModel turCmsModel, AemObject aemObject,
                                       List<TurSNAttributeSpec> targetAttrDefinitions) {
        switch (Objects.requireNonNull(getContentType())) {
            case CQ_PAGE:
                addTurSNJobItemToIndex(aemObject, turCmsModel, targetAttrDefinitions);
                break;
            case DAM_ASSET:
                if (!StringUtils.isEmpty(turCmsModel.getSubType())) {
                    if (turCmsModel.getSubType().equals(CONTENT_FRAGMENT) && aemObject.isContentFragment()) {
                        aemObject.setDataPath(DATA_MASTER);
                        addTurSNJobItemToIndex(aemObject, turCmsModel, targetAttrDefinitions);
                    } else if (turCmsModel.getSubType().equals(STATIC_FILE)) {
                        aemObject.setDataPath(METADATA);
                        addTurSNJobItemToIndex(aemObject, turCmsModel, targetAttrDefinitions);
                    }
                }
                break;
        }
    }

    private void itemsProcessedStatus() {
        if (processed == 0) {
            currentPage++;
        }
        if (processed >= jobSize) {
            processed = 0;
            start = System.currentTimeMillis();
        } else {
            processed++;
        }
    }

    private TurCmsTargetAttrValueList runCustomClassFromContentType(TurCmsModel turCmsModel, AemObject aemObject) {
        try {
            if (!StringUtils.isEmpty(turCmsModel.getClassName()))
                return ((ExtContentInterface) Class.forName(turCmsModel.getClassName())
                        .getDeclaredConstructor().newInstance())
                        .consume(aemObject, getTurAemContext());
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        return new TurCmsTargetAttrValueList();
    }

    private void deIndexObject() {
        turAemIndexingRepository.findContentsShouldBeDeIndexed(getGroup(), deltaId).ifPresent(contents -> {
                    contents.forEach(content -> {
                        log.info(String.format("deIndex %s object from %s group and %s delta",
                                content.getAemId(), getGroup(), deltaId));
                        Map<String, Object> attributes = new HashMap<>();
                        attributes.put("id", content.getAemId());
                        attributes.put("source_apps",
                              this.turAemSource.getProviderName());
                        addJobItemToItems(new TurSNJobItem(TurSNJobAction.DELETE,
                                Collections.singletonList(this.turAemSource.getTurSNSite()),
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
            final Locale locale = TurAemUtils.getLocaleFromAemObject(this.turAemSource, aemObject);
            if (objectNeedBeIndexed(aemObject)) {
                createIndexingStatus(aemObject, locale);
                sendToTuringToBeIndexed(aemObject, turCmsModel, turSNAttributeSpecList, locale);
            } else {
                if (objectNeedBeReIndexed(aemObject)) {
                    turAemIndexingRepository.findByAemIdAndIndexGroup(aemObject.getPath(), getGroup())
                            .ifPresent(turAemIndexingsList ->
                                    log.info(String.format("ReIndexed %s object (%s) from %tc to %tc and deltaId = %s",
                                            aemObject.getPath(), getGroup(), turAemIndexingsList.getFirst().getDate(),
                                            getDeltaDate(aemObject), deltaId)));
                    sendToTuringToBeIndexed(aemObject, turCmsModel, turSNAttributeSpecList, locale);
                }
                updateIndexingStatus(aemObject, locale);
            }

        }
    }

    private void sendToTuringToBeIndexed(AemObject aemObject, TurCmsModel turCmsModel,
                                         List<TurSNAttributeSpec> turSNAttributeSpecList, Locale locale) {
        TurAEMAttrProcess turAEMAttrProcess = new TurAEMAttrProcess();
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = turAEMAttrProcess
                .prepareAttributeDefs(aemObject, turCmsContentDefinitionProcess, turSNAttributeSpecList, getTurAemContext());
        mergeTargetAttrValueCMSWithCustomClass(turCmsTargetAttrValueList,
                runCustomClassFromContentType(turCmsModel, aemObject));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(SITE, siteName);
        turCmsTargetAttrValueList.stream()
                .filter(turCmsTargetAttrValue -> !CollectionUtils.isEmpty(turCmsTargetAttrValue.getMultiValue()))
                .forEach(targetAttrValue -> {
                    String attributeName = targetAttrValue.getTargetAttrName();
                    targetAttrValue.getMultiValue().forEach(attributeValue -> {
                        if (!StringUtils.isBlank(attributeValue)) {
                            if (attributes.containsKey(attributeName)) {
                                addItemInExistingAttribute(attributeValue, attributes, attributeName);
                            } else {
                                addFirstItemToAttribute(attributeName, attributeValue, attributes);
                            }
                        }
                    });
                });
        addJobItemToItems(new TurSNJobItem(TurSNJobAction.CREATE,
                locale, Collections.singletonList(this.turAemSource.getTurSNSite()), castSpecToJobSpec(
                getDefinitionFromModel(turSNAttributeSpecList, attributes)),
                attributes));
    }

    private void updateIndexingStatus(AemObject aemObject, Locale locale) {
        turAemIndexingRepository.findByAemIdAndIndexGroup(aemObject.getPath(), getGroup())
                .filter(turAemIndexingList -> !turAemIndexingList.isEmpty())
                .ifPresent(turAemIndexingList -> {
                    if (turAemIndexingList.size() > 1) {
                        turAemIndexingRepository.deleteByAemIdAndIndexGroup(aemObject.getPath(), getGroup());
                        log.info(String.format("Removed duplicated %s object (%s)",
                                aemObject.getPath(), getGroup()));
                        turAemIndexingRepository.save(createTurAemIndexing(aemObject, locale));
                        log.info(String.format("Recreated %s object (%s) and deltaId = %s",
                                aemObject.getPath(), getGroup(), deltaId));
                    } else {
                        turAemIndexingRepository.save(turAemIndexingList.getFirst()
                                .setDate(getDeltaDate(aemObject))
                                .setDeltaId(deltaId));
                        log.info(String.format("Updated %s object (%s) deltaId = %s",
                                aemObject.getPath(), getGroup(), deltaId));
                    }
                });
    }

    private void createIndexingStatus(AemObject aemObject, Locale locale) {
        turAemIndexingRepository.save(createTurAemIndexing(aemObject, locale));
        log.info(String.format("Created %s object (%s)", aemObject.getPath(), getGroup()));
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

    private void mergeTargetAttrValueCMSWithCustomClass(TurCmsTargetAttrValueList turCmsTargetAttrValueList,
                                                        TurCmsTargetAttrValueList turCmsTargetAttrValueCustomClassList) {
        turCmsTargetAttrValueCustomClassList.forEach(targetAttrValueFromClass ->
                turCmsTargetAttrValueList.stream()
                        .filter(targetAttrValue ->
                                targetAttrValue.getTargetAttrName()
                                        .equals(targetAttrValueFromClass.getTargetAttrName()))
                        .findFirst()
                        .ifPresentOrElse(targetAttrValue ->
                                        targetAttrValue.setMultiValue(targetAttrValueFromClass.getMultiValue()),
                                () -> turCmsTargetAttrValueList.add(targetAttrValueFromClass)));
    }

    private boolean objectNeedBeIndexed(AemObject aemObject) {
        return (!StringUtils.isEmpty(aemObject.getPath()) &&
                !turAemIndexingRepository.existsByAemIdAndIndexGroup(aemObject.getPath(), getGroup()));
    }

    private boolean objectNeedBeReIndexed(AemObject aemObject) {
        return !StringUtils.isEmpty(aemObject.getPath()) &&
                turAemIndexingRepository.existsByAemIdAndIndexGroupAndDateNot(aemObject.getPath(),
                        getGroup(), getDeltaDate(aemObject));
    }

    @NotNull
    private static Date getDeltaDate(AemObject aemObject) {
        return aemObject.getLastModified() != null ?
                aemObject.getLastModified().getTime() :
                aemObject.getCreatedDate() != null ?
                        aemObject.getCreatedDate().getTime() :
                        new Date();
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

    private void addFirstItemToAttribute(String attributeName,
                                         String attributeValue,
                                         Map<String, Object> attributes) {
        attributes.put(attributeName, attributeValue);
    }
}