package com.viglet.turing.connector.aem.indexer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.*;
import com.viglet.turing.connector.aem.indexer.conf.AemHandlerConfiguration;
import com.viglet.turing.connector.aem.indexer.ext.ExtContentInterface;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemIndexing;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSystem;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemIndexingRepository;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemSystemRepository;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValue;
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
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
    private static final String CQ_PAGE = "cq:Page";
    private static final String DAM_ASSET = "dam:Asset";
    public static final String ID_ATTR = "id";
    private static int processed = 0;
    private static int currentPage = 0;
    private static long start;
    private AemHandlerConfiguration config = null;
    private String siteName;
    private final String deltaId = UUID.randomUUID().toString();
    private TurCmsContentDefinitionProcess turCmsContentDefinitionProcess;

    private final Set<String> visitedLinks = new HashSet<>();
    private final Queue<String> remainingLinks = new LinkedList<>();
    private TurSNJobItems turSNJobItems = new TurSNJobItems();
    private final String turingUrl;
    private final String turingApiKey;
    private final int timeout;
    private final int jobSize;
    private String snSite;

    private String propertyPath;
    private boolean reindex;
    private String group;
    private boolean dryRun;
    private boolean reindexOnce;
    private String contentType;
    private String guidFilePath;
    private List<String> rootPaths;
    private boolean showOutput;
    private String hostAndPort;
    private String username;
    private String password;

    private final String subType = "NONE";

    private final TurAemIndexingRepository turAemIndexingRepository;
    private final TurAemSystemRepository turAemSystemRepository;

    @Inject
    public TurAemIndexerTool(@Value("${turing.url}") String turingUrl,
                             @Value("${turing.apiKey}") String turingApiKey,
                             @Value("${turing.aem.timeout:5000}") int timeout,
                             @Value("${turing.aem.job.size:50}") int jobSize,
                             TurAemIndexingRepository turAemIndexingRepository,
                             TurAemSystemRepository turAemSystemRepository) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
        this.timeout = timeout;
        this.jobSize = jobSize;
        this.turAemIndexingRepository = turAemIndexingRepository;
        this.turAemSystemRepository = turAemSystemRepository;
    }

    private void run() {
        config = new AemHandlerConfiguration(propertyPath);
        turCmsContentDefinitionProcess = new TurCmsContentDefinitionProcess(config,
                Paths.get(propertyPath).toAbsolutePath().getParent());
        try {
            if (reindex) {
                turAemIndexingRepository.deleteContentsToReindex(group);
            }
            if (reindexOnce) {
                turAemIndexingRepository.deleteContentsToReindexOnce(group);
            }
            this.getNodesFromJson();
            if (!dryRun && !usingGuidParameter()) deIndexObject();
            updateSystemOnce();

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
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
        return group + "/" + ONCE;
    }

    private void getNodesFromJson() {
        if (usingContentTypeParameter()) {
            turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(contentType)
                    .ifPresentOrElse(cmsModel -> jsonByContentType(),
                            () -> System.out.printf("%s type is not configured in CTD Mapping XML file.%n",
                                    contentType));
        } else if (usingGuidParameter()) {
            jsonByGuidList();
        }
    }

    private boolean usingContentTypeParameter() {
        return StringUtils.isNotBlank(contentType);
    }

    private boolean usingGuidParameter() {
        return StringUtils.isNotBlank(guidFilePath);
    }

    private void jsonByGuidList() {
        ArrayList<String> contentInstances;
        try (FileReader fr = new FileReader(guidFilePath);
             BufferedReader br = new BufferedReader(fr)) {
            contentInstances = br.lines().collect(Collectors.toCollection(ArrayList::new));
            if (!contentInstances.isEmpty())
                this.indexGUIDList(contentInstances);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void indexGUIDList(List<String> guids) {
        guids.stream().filter(guid -> !StringUtils.isEmpty(guid)).forEach(guid -> {
            start = System.currentTimeMillis();
            rootPaths.forEach(rootPath ->
                    siteName = TurAemUtils.getInfinityJson(rootPath, this).getJSONObject(JCR_CONTENT)
                            .getString(JCR_TITLE));
            final JSONObject jsonObject = TurAemUtils.getInfinityJson(guid, this);
            contentType = jsonObject.getString(JCR_PRIMARY_TYPE);
            getNodeFromJson(guid, jsonObject);
            long elapsed = System.currentTimeMillis() - start;
            System.out.printf("%d items processed in %dms%n", processed, elapsed);
        });
    }

    private void jsonByContentType() {
        rootPaths.forEach(rootPath -> {
            JSONObject jsonSite = TurAemUtils.getInfinityJson(rootPath, this);
            setSiteName(rootPath, jsonSite);
            if (visitedLinks.add(rootPath) && !remainingLinks.offer(rootPath)) {
                log.error("Item didn't add to queue: " + rootPath);
            }

        });
        while (!remainingLinks.isEmpty()) {
            String url = remainingLinks.poll();
            JSONObject jsonObject = TurAemUtils.getInfinityJson(url, this);
            turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(contentType).ifPresent(model ->
                    addTurSNJobItemByType(model, new AemObject(url, jsonObject),
                            turCmsContentDefinitionProcess.getTargetAttrDefinitions()));
            sendToTuringWhenMaxSize();
            getInfoQueue();
            start = System.currentTimeMillis();
            if (jsonObject.has(JCR_PRIMARY_TYPE) && jsonObject.getString(JCR_PRIMARY_TYPE).equals(contentType)) {
                getNodeFromJson(url, jsonObject);
            }
        }
    }

    private void getNodeFromJson(String url, JSONObject jsonObject) {
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith(JCR) && (getSubType().equals(STATIC_FILE_SUB_TYPE)
                    || !checkIfFileHasImageExtension(key))) {
                String urlChild = url + "/" + key;
                if (!isOnce() || !isOnceConfig(urlChild)) {
                    if (visitedLinks.add(urlChild) && !remainingLinks.offer(urlChild)) {
                        log.error("Item didn't add to queue: " + urlChild);
                    }
                }
            }
        });
    }

    private void getInfoQueue() {
        log.info("Total Job Item: " + Iterators.size(turSNJobItems.iterator()));
        log.info("Total Visited Links: " + (long) visitedLinks.size());
        log.info("Queue Size: " + (long) remainingLinks.size());
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
                log.debug("TurSNJobItem Id: " + turSNJobItem.getAttributes().get(ID_ATTR));
            }
        }
        try {
            TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(URI.create(turingUrl).toURL(), snSite,
                            turingApiKey),
                    false);
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }

    }

    private void setSiteName(String rootPath, JSONObject jsonSite) {
        if (jsonSite.has(JCR_CONTENT) && jsonSite.getJSONObject(JCR_CONTENT).has(JCR_TITLE)) {
            siteName = jsonSite.getJSONObject(JCR_CONTENT).getString(JCR_TITLE);
        } else {
            log.error(String.format("No site name the %s root path (%s)", rootPath, group));
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

    public static boolean checkIfFileHasImageExtension(String s) {
        String[] imageExtensions = {".jpg", ".png", ".jpeg", ".svg", ".webp"};
        return Arrays.stream(imageExtensions).anyMatch(suffix -> s.toLowerCase().endsWith(suffix));
    }

    private boolean isOnce() {
        return turAemSystemRepository.findByConfig(configOnce()).map(TurAemSystem::isBooleanValue).orElse(false);
    }

    private boolean isOnceConfig(String path) {
        if (StringUtils.isNotBlank(config.getOncePatternPath())) {
            Pattern p = Pattern.compile(config.getOncePatternPath());
            Matcher m = p.matcher(path);
            return m.lookingAt();
        }
        return false;
    }

    private void addTurSNJobItemByType(TurCmsModel turCmsModel, AemObject aemObject,
                                       List<TurSNAttributeSpec> targetAttrDefinitions) {
        switch (Objects.requireNonNull(contentType)) {
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
                        .consume(aemObject, config, this);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        return new TurCmsTargetAttrValueList();
    }

    private void deIndexObject() {
        turAemIndexingRepository.findContentsShouldBeDeIndexed(group, deltaId).ifPresent(contents -> {
                    contents.forEach(content -> {
                        log.info(String.format("deIndex %s object from %s group and %s delta",
                                content.getAemId(), group, deltaId));
                        Map<String, Object> attributes = new HashMap<>();
                        attributes.put(AemHandlerConfiguration.ID_ATTRIBUTE, content.getAemId());
                        attributes.put(AemHandlerConfiguration.PROVIDER_ATTRIBUTE,
                                AemHandlerConfiguration.DEFAULT_PROVIDER);
                        sendJobToTuring(new TurSNJobItems(new TurSNJobItem(TurSNJobAction.DELETE,
                                content.getLocale(), attributes)));
                    });
                    turAemIndexingRepository.deleteContentsWereDeIndexed(group, deltaId);
                }
        );
    }

    private void addTurSNJobItemToIndex(AemObject aemObject, TurCmsModel turCmsModel,
                                        List<TurSNAttributeSpec> turSNAttributeSpecList) {
        itemsProcessedStatus();
        if (dryRun || objectNeedBeIndexed(aemObject)) {
            final Locale locale = TurAemUtils.getLocaleFromAemObject(config, aemObject);
            if (!dryRun) {
                turAemIndexingRepository.save(new TurAemIndexing()
                        .setAemId(aemObject.getPath())
                        .setIndexGroup(group)
                        .setDate(aemObject.getLastModified().getTime())
                        .setDeltaId(deltaId)
                        .setOnce(isOnceConfig(aemObject.getPath()))
                        .setLocale(locale));
                log.info(String.format("Created %s object (%s)", aemObject.getPath(), group));
            }
            TurAEMAttrProcess turAEMAttrProcess = new TurAEMAttrProcess();
            TurCmsTargetAttrValueList turCmsTargetAttrValueList = turAEMAttrProcess
                    .prepareAttributeDefs(aemObject, turCmsContentDefinitionProcess, turSNAttributeSpecList, this);
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
                                    addFirstItemToAttribute(targetAttrValue, attributeValue, attributes);
                                    addFirstItemToAttribute(targetAttrValue, attributeValue, attributes);
                                }
                            }
                        });
                    });
            turSNJobItems.add(new TurSNJobItem(TurSNJobAction.CREATE,
                    locale, castSpecToJobSpec(
                    getDefinitionFromModel(turSNAttributeSpecList, attributes)),
                    attributes));
        } else if (!dryRun) {
            turAemIndexingRepository.findByAemIdAndIndexGroup(aemObject.getPath(), group).ifPresent(
                    turAemIndexingList -> {
                        turAemIndexingRepository.save(turAemIndexingList.getFirst()
                                .setDeltaId(deltaId));
                        log.info(String.format("Updated %s object (%s)", aemObject.getPath(), group));
                    });
        }
    }

    @NotNull
    private static List<TurSNJobAttributeSpec> castSpecToJobSpec(List<TurSNAttributeSpec> turSNAttributeSpecList) {
        return turSNAttributeSpecList.stream()
                .filter(Objects::nonNull)
                .map(TurSNJobAttributeSpec.class::cast)
                .collect(Collectors.toList());
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
        return (!StringUtils.isEmpty(aemObject.getPath()) && aemObject.getLastModified() != null &&
                !turAemIndexingRepository.existsByAemIdAndDateAndIndexGroup(aemObject.getPath(),
                        aemObject.getLastModified().getTime(), group));
    }

    private void sendJobToTuring(TurSNJobItems turSNJobItems) {
        showOutput(turSNJobItems);
        if (!dryRun) {
            turSNJobItems.getTuringDocuments().stream().findFirst().ifPresent(document ->
                    log.info(String.format("Send %s object job (%s) to Turing",
                            document.getAttributes().get(ID), group)));
            TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(config.getTuringURL(), config.getDefaultSNSiteConfig().getName(),
                            config.getApiKey()),
                    false);
        }
    }

    private void showOutput(TurSNJobItems turSNJobItems) {
        if (showOutput) try {
            System.out.println(new ObjectMapper().writeValueAsString(turSNJobItems));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void addFirstItemToAttribute(TurCmsTargetAttrValue targetAttrValue,
                                         String attributeValue,
                                         Map<String, Object> attributes) {
        attributes.put(targetAttrValue.getTargetAttrName(), attributeValue);
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
}