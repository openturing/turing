package com.viglet.turing.connector.aem.indexer;

import ch.qos.logback.classic.Level;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.job.*;
import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.TurAEMCommonsUtils;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.ExtContentInterface;
import com.viglet.turing.connector.aem.indexer.conf.AemHandlerConfiguration;
import com.viglet.turing.connector.aem.indexer.persistence.TurAemIndexing;
import com.viglet.turing.connector.aem.indexer.persistence.TurAemIndexingDAO;
import com.viglet.turing.connector.aem.indexer.persistence.TurAemSystem;
import com.viglet.turing.connector.aem.indexer.persistence.TurAemSystemDAO;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueList;
import com.viglet.turing.connector.cms.mappers.TurCmsContentDefinitionProcess;
import com.viglet.turing.connector.cms.mappers.TurCmsModel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Getter
public class TurAEMIndexerTool {
    public static final String JCR_TITLE = "jcr:title";
    public static final String JCR_CONTENT = "jcr:content";
    public static final String JCR_PRIMARY_TYPE = "jcr:primaryType";
    public static final String CONTENT_FRAGMENT = "content-fragment";
    private static final JCommander jCommander = new JCommander();
    public static final String STATIC_FILE = "static-file";
    public static final String SITE = "site";
    public static final String DATA_MASTER = "data/master";
    public static final String METADATA = "metadata";
    public static final String JCR = "jcr:";
    public static final String ID = "id";
    public static final String STATIC_FILE_SUB_TYPE = "STATIC_FILE";
    public static final String ONCE = "once";
    public static final String REP = "rep:";
    @Parameter(names = {"--host",
            "-h"}, description = "The host on which Content Management server is installed.")
    private String hostAndPort = null;
    @Parameter(names = {"--username",
            "-u"}, description = "A username to log in to the Content Management Server.")
    private String username = null;
    @Parameter(names = {"--password", "-p"}, description = "The password for the user name.")
    private String password = null;
    @Parameter(names = {"--all", "-a"}, description = "Index all instances of all content types and object types.")
    private boolean allObjectTypes = false;
    @Parameter(names = {"--content-type",
            "-c"}, description = "Type of model whose instances are to be indexed.")
    private String contentType = null;
    @Parameter(names = {"--sub-type",
            "-s"}, description = "Syt type of model whose instances are to be indexed.")
    private String subType = "NONE";
    @Parameter(names = {"--guids",
            "-g"}, description = "The path to a file containing the GUID(s) of content instances or static files to be indexed.")
    private String guidFilePath = null;
    @Parameter(names = {"--root-paths", "-r"}, description = "AEM root paths, you need use comma-separated.")
    private List<String> rootPaths = List.of("/content/we-retail");
    @Parameter(names = "--delivered", description = "Publish delivery or author site", help = true)
    private boolean delivered = false;
    @Parameter(names = {"--page-size",
            "-z"}, description = "The page size. After processing a page the processed count is written to an offset file."
            + " This helps the indexer to resume from that page even after failure. ")
    private int pageSize = 50;
    @Parameter(names = "--debug", description = "Change the log level to debug", help = true)
    private boolean debug = false;
    @Parameter(names = "--property", description = "Property file location path", help = true, required = true)
    private String propertyPath = "turing-aem.properties";
    @Parameter(names = "--group", description = "Identifier to verify delta updates", help = true, required = true)
    private String group;
    @Parameter(names = "--reindex", description = "Reindex all content except once pattern", help = true)
    private boolean reindex = false;
    @Parameter(names = "--reindex-once", description = "Reindex only once pattern", help = true)
    private boolean reindexOnce = false;
    @Parameter(names = "--show-output", description = "Property file location path", help = true)
    private boolean showOutput = false;
    @Parameter(names = "--dry-run", description = "Execute without connect to Turing", help = true)
    private boolean dryRun = false;
    @Parameter(names = "--help", description = "Print usage instructions", help = true)
    private boolean help = false;
    private static final String CQ_PAGE = "cq:Page";
    private static final String DAM_ASSET = "dam:Asset";
    private static int processed = 0;
    private static int currentPage = 0;
    private static long start;
    private AemHandlerConfiguration config = null;
    private String siteName;
    private final String deltaId = UUID.randomUUID().toString();
    private final TurAemIndexingDAO turAemIndexingDAO = new TurAemIndexingDAO();
    private final TurAemSystemDAO turAemSystemDAO = new TurAemSystemDAO();
    private TurCmsContentDefinitionProcess turCmsContentDefinitionProcess;

    public static void main(String... argv) {
        TurAEMIndexerTool turAEMIndexerTool = new TurAEMIndexerTool();
        jCommander.addObject(turAEMIndexerTool);
        try {
            jCommander.parse(argv);
            if (turAEMIndexerTool.debug) {
                ((ch.qos.logback.classic.Logger) LoggerFactory.
                        getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(Level.DEBUG);
            }
            if (turAEMIndexerTool.help) {
                jCommander.usage();
                return;
            }
            jCommander.getConsole().println("Viglet Turing AEM Indexer Tool.");
            turAEMIndexerTool.run();
        } catch (ParameterException e) {
            log.info("Error: {}", e.getLocalizedMessage());
            jCommander.usage();
        }
    }

    private void run() {
        config = new AemHandlerConfiguration(propertyPath);
        turCmsContentDefinitionProcess = new TurCmsContentDefinitionProcess(config,
                Paths.get(propertyPath).toAbsolutePath().getParent());
        TurAemSourceContext turAemSourceContext = getTurAemSourceContext(config, this);
        try {
            if (reindex) {
                turAemIndexingDAO.deleteContentsToReindex(group);
            }
            if (reindexOnce) {
                turAemIndexingDAO.deleteContentsToReindexOnce(group);
            }
            this.getNodesFromJson(turAemSourceContext);
            if (!dryRun && !usingGuidParameter()) deIndexObject();
            updateSystemOnce();
            turAemIndexingDAO.close();
            turAemSystemDAO.close();

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private TurAemSourceContext getTurAemSourceContext(AemHandlerConfiguration config,
                                                       TurAEMIndexerTool turAEMIndexerTool) {
        return TurAemSourceContext.builder()
                .group(config.getCmsGroup())
                .contentType(config.getCmsContentType())
                .defaultLocale(config.getDefaultSNSiteConfig().getLocale())
                .rootPath(turAEMIndexerTool.rootPaths.getFirst())
                .url(config.getCmsHost())
                .siteName(turAEMIndexerTool.getSiteName())
                .subType(turAEMIndexerTool.getSubType())
                .turSNSite(config.getDefaultSNSiteConfig().getName())
                .oncePattern(config.getOncePatternPath())
                .providerName(config.getProviderName())
                .password(config.getCmsPassword())
                .urlPrefix(config.getCDAURLPrefix())
                .username(config.getCmsUsername())
                .localePaths(config.getLocales())
                .build();
    }

    private void updateSystemOnce() {
        turAemSystemDAO.findByConfig(configOnce()).ifPresentOrElse(turAemSystem -> {
                    turAemSystem.setBooleanValue(true);
                    turAemSystemDAO.update(turAemSystem);
                },
                () -> turAemSystemDAO.save(new TurAemSystem(configOnce(), true)));
    }

    @NotNull
    private String configOnce() {
        return "%s/%s".formatted(group, ONCE);
    }

    private void getNodesFromJson(TurAemSourceContext turAemSourceContext) {
        if (usingContentTypeParameter()) {
            turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(contentType)
                    .ifPresentOrElse(turCmsModel -> jsonByContentType(turAemSourceContext),
                            () -> jCommander.getConsole()
                                    .println(String.format("%s type is not configured in CTD Mapping XML file.",
                                            contentType)));
        } else if (usingGuidParameter()) {
            jsonByGuidList(turAemSourceContext);
        }
    }

    private boolean usingContentTypeParameter() {
        return StringUtils.isNotBlank(contentType);
    }

    private boolean usingGuidParameter() {
        return StringUtils.isNotBlank(guidFilePath);
    }

    private void jsonByGuidList(TurAemSourceContext turAemSourceContext) {
        ArrayList<String> contentInstances;
        try (FileReader fr = new FileReader(guidFilePath);
             BufferedReader br = new BufferedReader(fr)) {
            contentInstances = br.lines().collect(Collectors.toCollection(ArrayList::new));
            if (!contentInstances.isEmpty())
                this.indexGUIDList(contentInstances, turAemSourceContext);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void indexGUIDList(List<String> guids, TurAemSourceContext turAemSourceContext) {
        jCommander.getConsole().println(String.format("Processing a total of %d GUID Strings", guids.size()));
        guids.stream().filter(guid -> !StringUtils.isEmpty(guid)).forEach(guid -> {
            start = System.currentTimeMillis();
            rootPaths.forEach(rootPath ->
                    siteName = TurAEMCommonsUtils.getInfinityJson(rootPath, turAemSourceContext).getJSONObject(JCR_CONTENT)
                            .getString(JCR_TITLE));
            final JSONObject jsonObject = TurAEMCommonsUtils.getInfinityJson(guid, turAemSourceContext);
            contentType = jsonObject.getString(JCR_PRIMARY_TYPE);
            getNodeFromJson(guid, jsonObject, turAemSourceContext);
            long elapsed = System.currentTimeMillis() - start;
            jCommander.getConsole().println(String.format("%d items processed in %dms", processed, elapsed));
        });
    }

    private void jsonByContentType(TurAemSourceContext turAemSourceContext) {
        rootPaths.forEach(rootPath -> {
            JSONObject jsonSite = TurAEMCommonsUtils.getInfinityJson(rootPath, turAemSourceContext);
            start = System.currentTimeMillis();
            setSiteName(rootPath, jsonSite);
            getNodeFromJson(rootPath, jsonSite, turAemSourceContext);
            jCommander.getConsole().println(String.format("%d items processed in %dms", processed,
                    System.currentTimeMillis() - start));
        });
    }

    private void setSiteName(String rootPath, JSONObject jsonSite) {
        if (jsonSite.has(JCR_CONTENT) && jsonSite.getJSONObject(JCR_CONTENT).has(JCR_TITLE)) {
            siteName = jsonSite.getJSONObject(JCR_CONTENT).getString(JCR_TITLE);
        } else {
            log.error(String.format("No site name the %s root path (%s)", rootPath, group));
        }
    }

    public static String ordinal(int i) {
        String[] suffixes = new String[]{"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};
        return switch (i % 100) {
            case 11, 12, 13 -> i + "th";
            default -> i + suffixes[i % 10];
        };
    }

    private void getNodeFromJson(String nodePath, JSONObject jsonObject, TurAemSourceContext turAemSourceContext) {
        if (jsonObject.has(JCR_PRIMARY_TYPE) && jsonObject.getString(JCR_PRIMARY_TYPE).equals(contentType)) {
            turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(contentType).ifPresent(model ->
                    prepareIndexObject(model, new AemObject(nodePath, jsonObject),
                            turCmsContentDefinitionProcess.getTargetAttrDefinitions(), turAemSourceContext));
        }
        getChildrenFromJson(nodePath, jsonObject, turAemSourceContext);
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

    private void getChildrenFromJson(String nodePath, JSONObject jsonObject, TurAemSourceContext turAemSourceContext) {
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith(JCR) && !key.startsWith(REP) && (getSubType().equals(STATIC_FILE_SUB_TYPE)
                    || !checkIfFileHasImageExtension(key))) {
                String nodePathChild = "%s/%s".formatted(nodePath, key);
                if (!isOnce() || !isOnceConfig(nodePathChild)) {
                    getNodeFromJson(nodePathChild, TurAEMCommonsUtils.getInfinityJson(nodePathChild, turAemSourceContext),
                            turAemSourceContext);
                }
            }
        });
    }

    private boolean isOnce() {
        return turAemSystemDAO.findByConfig(configOnce()).map(TurAemSystem::isBooleanValue).orElse(false);
    }

    private boolean isOnceConfig(String path) {
        if (StringUtils.isNotBlank(config.getOncePatternPath())) {
            Pattern p = Pattern.compile(config.getOncePatternPath());
            Matcher m = p.matcher(path);
            return m.lookingAt();
        }
        return false;
    }

    private void prepareIndexObject(TurCmsModel turCmsModel, AemObject aemObject,
                                    List<TurSNAttributeSpec> targetAttrDefinitions,
                                    TurAemSourceContext turAemSourceContext) {
        switch (Objects.requireNonNull(contentType)) {
            case CQ_PAGE:
                indexObject(aemObject, turCmsModel, targetAttrDefinitions, turAemSourceContext);
                break;
            case DAM_ASSET:
                if (!StringUtils.isEmpty(turCmsModel.getSubType())) {
                    if (turCmsModel.getSubType().equals(CONTENT_FRAGMENT) && aemObject.isContentFragment()) {
                        aemObject.setDataPath(DATA_MASTER);
                        indexObject(aemObject, turCmsModel, targetAttrDefinitions, turAemSourceContext);
                    } else if (turCmsModel.getSubType().equals(STATIC_FILE)) {
                        aemObject.setDataPath(METADATA);
                        indexObject(aemObject, turCmsModel, targetAttrDefinitions, turAemSourceContext);
                    }
                }
                break;
        }
    }

    private void itemsProcessedStatus() {
        if (processed == 0) {
            currentPage++;
            jCommander.getConsole().println(String.format("Processing %s item",
                    ordinal((currentPage * pageSize) - pageSize + 1)));
        }
        if (processed >= pageSize) {
            jCommander.getConsole().println(String.format("%d items processed in %dms", processed,
                    System.currentTimeMillis() - start));
            processed = 0;
            start = System.currentTimeMillis();
        } else {
            processed++;
        }
    }

    private TurCmsTargetAttrValueList runCustomClassFromContentType(TurCmsModel turCmsModel, AemObject aemObject,
                                                                    TurAemSourceContext turAemSourceContext) {
        try {
            if (!StringUtils.isEmpty(turCmsModel.getClassName()))
                return ((ExtContentInterface) Class.forName(turCmsModel.getClassName())
                        .getDeclaredConstructor().newInstance())
                        .consume(aemObject, turAemSourceContext);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        return new TurCmsTargetAttrValueList();
    }

    private void deIndexObject() {
        turAemIndexingDAO.findContentsShouldBeDeIndexed(group, deltaId).ifPresent(contents -> {
                    jCommander.getConsole().println("DeIndex Content that were removed...");
                    contents.forEach(content -> {
                        log.info(String.format("deIndex %s object from %s group and %s delta",
                                content.getAemId(), group, deltaId));
                        Map<String, Object> attributes = new HashMap<>();
                        attributes.put(AemHandlerConfiguration.ID_ATTRIBUTE, content.getAemId());
                        attributes.put(AemHandlerConfiguration.PROVIDER_ATTRIBUTE,
                                AemHandlerConfiguration.DEFAULT_PROVIDER);
                        sendJobToTuring(new TurSNJobItems(new TurSNJobItem(TurSNJobAction.DELETE,
                                Collections.singletonList(config.getDefaultSNSiteConfig().getName()),
                                content.getLocale(), attributes)));
                    });
                    turAemIndexingDAO.deleteContentsWereDeIndexed(group, deltaId);
                }
        );
    }

    private boolean objectNeedBeIndexed(AemObject aemObject) {
        return (!StringUtils.isEmpty(aemObject.getPath()) &&
                !turAemIndexingDAO.existsByAemIdAndGroup(aemObject.getPath(), group));
    }

    private boolean objectNeedBeReIndexed(AemObject aemObject) {
        return !StringUtils.isEmpty(aemObject.getPath()) &&
                turAemIndexingDAO.existsByAemIdAndGroupAndDateNotEqual(aemObject.getPath(),
                        group, getDeltaDate(aemObject));
    }

    private void indexObject(AemObject aemObject, TurCmsModel turCmsModel,
                             List<TurSNAttributeSpec> turSNAttributeSpecList, TurAemSourceContext turAemSourceContext) {
        itemsProcessedStatus();
        if (!dryRun) {
            if (!delivered || aemObject.isDelivered()) {
                final Locale locale = TurAEMCommonsUtils.getLocaleFromAemObject(turAemSourceContext, aemObject);
                if (objectNeedBeIndexed(aemObject)) {
                    createIndexingStatus(aemObject, locale);
                    sendToTuringToBeIndexed(aemObject, turCmsModel, turSNAttributeSpecList, locale,
                            turAemSourceContext);
                } else {
                    if (objectNeedBeReIndexed(aemObject)) {
                        turAemIndexingDAO.findByAemIdAndGroup(aemObject.getPath(), group).ifPresent(turAemIndexingsList ->
                                log.info(String.format("ReIndexed %s object (%s) from %tc to %tc and deltaId = %s",
                                        aemObject.getPath(), group, turAemIndexingsList.getFirst().getDate(),
                                        getDeltaDate(aemObject), deltaId)));
                        sendToTuringToBeIndexed(aemObject, turCmsModel, turSNAttributeSpecList, locale,
                                turAemSourceContext);
                    }
                    updateIndexingStatus(aemObject, locale);
                }
            } else {
                log.info(String.format("Unpublished %s object (%s) deltaId = %s",
                        aemObject.getPath(), group, deltaId));
            }
        }
    }

    private void createIndexingStatus(AemObject aemObject, Locale locale) {
        turAemIndexingDAO.save(createTurAemIndexing(aemObject, locale));
        log.info(String.format("Created %s object (%s) and deltaId = %s", aemObject.getPath(), group, deltaId));
    }

    private void updateIndexingStatus(AemObject aemObject, Locale locale) {
        turAemIndexingDAO.findByAemIdAndGroup(aemObject.getPath(), group)
                .filter(turAemIndexingList -> !turAemIndexingList.isEmpty())
                .ifPresent(turAemIndexingList -> {
                    if (turAemIndexingList.size() > 1) {
                        turAemIndexingDAO.deleteByAemIdAndGroup(aemObject.getPath(), group);
                        log.info(String.format("Removed duplicated %s object (%s)",
                                aemObject.getPath(), group));
                        turAemIndexingDAO.save(createTurAemIndexing(aemObject, locale));
                        log.info(String.format("Recreated %s object (%s) and deltaId = %s",
                                aemObject.getPath(), group, deltaId));
                    } else {
                        turAemIndexingDAO.update(turAemIndexingList.getFirst()
                                .setDate(getDeltaDate(aemObject))
                                .setDeltaId(deltaId));
                        log.info(String.format("Updated %s object (%s) deltaId = %s",
                                aemObject.getPath(), group, deltaId));
                    }
                });
    }

    private TurAemIndexing createTurAemIndexing(AemObject aemObject, Locale locale) {
        return new TurAemIndexing()
                .setAemId(aemObject.getPath())
                .setIndexGroup(group)
                .setDate(getDeltaDate(aemObject))
                .setDeltaId(deltaId)
                .setOnce(isOnceConfig(aemObject.getPath()))
                .setLocale(locale);
    }

    @NotNull
    private static Date getDeltaDate(AemObject aemObject) {
        return aemObject.getLastModified() != null ?
                aemObject.getLastModified().getTime() :
                aemObject.getCreatedDate() != null ?
                        aemObject.getCreatedDate().getTime() :
                        new Date();
    }

    private void sendToTuringToBeIndexed(AemObject aemObject, TurCmsModel turCmsModel,
                                         List<TurSNAttributeSpec> turSNAttributeSpecList, Locale locale,
                                         TurAemSourceContext turAemSourceContext) {
        TurAEMAttrProcess turAEMAttrProcess = new TurAEMAttrProcess();
        TurCmsTargetAttrValueList turCmsTargetAttrValueList = turAEMAttrProcess
                .prepareAttributeDefs(aemObject, turCmsContentDefinitionProcess, turSNAttributeSpecList,
                        turAemSourceContext);
        mergeTargetAttrValueCMSWithCustomClass(turCmsTargetAttrValueList,
                runCustomClassFromContentType(turCmsModel, aemObject, turAemSourceContext));
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
        sendJobToTuring(new TurSNJobItems(new TurSNJobItem(TurSNJobAction.CREATE, locale,
                Collections.singletonList(config.getDefaultSNSiteConfig().getName()), castSpecToJobSpec(
                getDefinitionFromModel(turSNAttributeSpecList, attributes)),
                attributes)));
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

    private void sendJobToTuring(TurSNJobItems turSNJobItems) {
        showOutput(turSNJobItems);
        if (!dryRun) {
            turSNJobItems.getTuringDocuments().stream().findFirst().ifPresent(document ->
                    log.info(String.format("Send %s object job (%s) to Turing",
                            document.getAttributes().get(ID), group)));
            TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(config.getTuringURL(), config.getDefaultSNSiteConfig().getName(),
                            new TurApiKeyCredentials(config.getApiKey())),
                    false);
        }
    }

    private void showOutput(TurSNJobItems turSNJobItems) {
        if (showOutput) try {
            jCommander.getConsole().println(new ObjectMapper().writeValueAsString(turSNJobItems));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void addFirstItemToAttribute(String attributeName,
                                         String attributeValue,
                                         Map<String, Object> attributes) {
        attributes.put(attributeName, attributeValue);
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
