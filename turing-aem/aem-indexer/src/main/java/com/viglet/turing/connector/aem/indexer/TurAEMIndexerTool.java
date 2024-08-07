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
import com.viglet.turing.commons.exception.TurRuntimeException;
import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.TurAEMAttrProcess;
import com.viglet.turing.connector.aem.commons.TurAEMCommonsUtils;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.indexer.conf.AemHandlerConfiguration;
import com.viglet.turing.connector.aem.indexer.persistence.TurAemIndexing;
import com.viglet.turing.connector.aem.indexer.persistence.TurAemIndexingDAO;
import com.viglet.turing.connector.aem.indexer.persistence.TurAemSystem;
import com.viglet.turing.connector.aem.indexer.persistence.TurAemSystemDAO;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueMap;
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
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
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
    public static final String ITEMS_PROCESSED_MESSAGE = "%d items processed in %dms";
    public static final String CQ = "cq:";
    @Parameter(names = {"--all", "-a"}, description = "Index all instances of all content types and object types.")
    private boolean allObjectTypes = false;
    @Parameter(names = {"--guids",
            "-g"}, description = "The path to a file containing the GUID(s) of content instances or static files to be indexed.")
    private String guidFilePath = null;
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
    private AemHandlerConfiguration config = null;
    private String siteName;
    private final String deltaId = UUID.randomUUID().toString();
    private final TurAemIndexingDAO turAemIndexingDAO = new TurAemIndexingDAO();
    private final TurAemSystemDAO turAemSystemDAO = new TurAemSystemDAO();
    private TurCmsContentDefinitionProcess turCmsContentDefinitionProcess;
    private AtomicInteger processed = new AtomicInteger(0);
    private AtomicInteger currentPage = new AtomicInteger(0);

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
        this.processed = new AtomicInteger(0);
        this.currentPage = new AtomicInteger(0);
        config = new AemHandlerConfiguration(propertyPath);
        turCmsContentDefinitionProcess = new TurCmsContentDefinitionProcess(config,
                Paths.get(propertyPath).toAbsolutePath().getParent());
        TurAemSourceContext turAemSourceContext = getTurAemSourceContext(config, this);
        try {
            if (reindex) {
                turAemIndexingDAO.deleteContentsToReindex(turAemSourceContext.getGroup());
            }
            if (reindexOnce) {
                turAemIndexingDAO.deleteContentsToReindexOnce(turAemSourceContext.getGroup());
            }
            this.getNodesFromJson(turAemSourceContext);
            if (!dryRun && !usingGuidParameter()) {
                deIndexObject(turAemSourceContext);
                updateSystemOnce(turAemSourceContext);
            }
            turAemIndexingDAO.close();
            turAemSystemDAO.close();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private TurAemSourceContext getTurAemSourceContext(AemHandlerConfiguration config,
                                                       TurAEMIndexerTool turAEMIndexerTool) {
        TurAemSourceContext turAemSourceContext = TurAemSourceContext.builder()
                .group(config.getCmsGroup())
                .contentType(config.getCmsContentType())
                .defaultLocale(config.getDefaultSNSiteConfig().getLocale())
                .rootPath(config.getCmsRootPath())
                .url(config.getCmsHost())
                .siteName(turAEMIndexerTool.getSiteName())
                .subType(config.getCmsSubType())
                .turSNSite(config.getDefaultSNSiteConfig().getName())
                .oncePattern(config.getOncePatternPath())
                .providerName(config.getProviderName())
                .password(config.getCmsPassword())
                .urlPrefix(config.getCDAURLPrefix())
                .username(config.getCmsUsername())
                .localePaths(config.getLocales())
                .build();
        log.info("TurAemSourceContext: {}", turAemSourceContext.toString());
        return turAemSourceContext;


    }

    private void updateSystemOnce(TurAemSourceContext turAemSourceContext) {
        turAemSystemDAO.findByConfig(configOnce(turAemSourceContext)).ifPresentOrElse(turAemSystem -> {
                    turAemSystem.setBooleanValue(true);
                    turAemSystemDAO.update(turAemSystem);
                },
                () -> turAemSystemDAO.save(new TurAemSystem(configOnce(turAemSourceContext), true)));
    }

    @NotNull
    private String configOnce(TurAemSourceContext turAemSourceContext) {
        return "%s/%s".formatted(turAemSourceContext.getGroup(), ONCE);
    }

    private void getNodesFromJson(TurAemSourceContext turAemSourceContext) {
        if (usingGuidParameter()) {
            jsonByGuidList(turAemSourceContext);
        } else if (usingContentTypeParameter(turAemSourceContext)) {
            turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(turAemSourceContext.getContentType())
                    .ifPresentOrElse(turCmsModel -> jsonByContentType(turAemSourceContext),
                            () -> jCommander.getConsole()
                                    .println("%s type is not configured in CTD Mapping XML file.".formatted(
                                            turAemSourceContext.getContentType())));
        }
    }

    private boolean usingContentTypeParameter(TurAemSourceContext turAemSourceContext) {
        return StringUtils.isNotBlank(turAemSourceContext.getContentType());
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
        jCommander.getConsole().println("Processing a total of %d GUID Strings".formatted(guids.size()));
        guids.stream().filter(guid -> !StringUtils.isEmpty(guid)).forEach(guid -> {
            long start = System.currentTimeMillis();
            TurAEMCommonsUtils.getInfinityJson(turAemSourceContext.getRootPath(), turAemSourceContext)
                    .ifPresent(jsonObject -> siteName = jsonObject.getJSONObject(JCR_CONTENT).getString(JCR_TITLE));
            TurAEMCommonsUtils.getInfinityJson(guid, turAemSourceContext).ifPresent(jsonObject -> {
                turAemSourceContext.setContentType(jsonObject.getString(JCR_PRIMARY_TYPE));
                getNodeFromJson(guid, jsonObject, turAemSourceContext, start);
                long elapsed = System.currentTimeMillis() - start;
                jCommander.getConsole().println(ITEMS_PROCESSED_MESSAGE.formatted(processed.get(), elapsed));
            });

        });
    }

    private void jsonByContentType(TurAemSourceContext turAemSourceContext) {
        TurAEMCommonsUtils.getInfinityJson(turAemSourceContext.getRootPath(), turAemSourceContext).ifPresent(jsonObject -> {
            long start = System.currentTimeMillis();
            TurAEMCommonsUtils.getSiteName(jsonObject).ifPresentOrElse(s -> this.siteName = s,
                    () -> log.error("No site name the {} root path ({})", turAemSourceContext.getRootPath(),
                            turAemSourceContext.getGroup()));
            getNodeFromJson(turAemSourceContext.getRootPath(), jsonObject, turAemSourceContext, start);
            jCommander.getConsole().println(ITEMS_PROCESSED_MESSAGE.formatted(processed.get(),
                    System.currentTimeMillis() - start));
        });


    }


    public static String ordinal(int i) {
        String[] suffixes = new String[]{"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};
        return switch (i % 100) {
            case 11, 12, 13 -> i + "th";
            default -> i + suffixes[i % 10];
        };
    }

    private void getNodeFromJson(String nodePath, JSONObject jsonObject, TurAemSourceContext turAemSourceContext,
                                 long start) {
        if (jsonObject.has(JCR_PRIMARY_TYPE) && jsonObject.getString(JCR_PRIMARY_TYPE).equals(turAemSourceContext.getContentType())) {
            turCmsContentDefinitionProcess.findByNameFromModelWithDefinition(turAemSourceContext.getContentType()).ifPresent(model ->
                    prepareIndexObject(model, new AemObject(nodePath, jsonObject),
                            turCmsContentDefinitionProcess.getTargetAttrDefinitions(), turAemSourceContext, start));
        }
        getChildrenFromJson(nodePath, jsonObject, turAemSourceContext, start);
    }


    private void getChildrenFromJson(String nodePath, JSONObject jsonObject, TurAemSourceContext turAemSourceContext,
                                     long start) {
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith(JCR) && !key.startsWith(REP) && !key.startsWith(CQ)
                    && (turAemSourceContext.getSubType().equals(STATIC_FILE_SUB_TYPE)
                    || TurAEMCommonsUtils.checkIfFileHasNotImageExtension(key))) {
                String nodePathChild = "%s/%s".formatted(nodePath, key);
                if (!isOnce(turAemSourceContext) || !isOnceConfig(nodePathChild)) {
                    TurAEMCommonsUtils.getInfinityJson(nodePathChild, turAemSourceContext).ifPresent(json ->
                            getNodeFromJson(nodePathChild, json, turAemSourceContext, start));
                }
            }
        });
    }

    private boolean isOnce(TurAemSourceContext turAemSourceContext) {
        return turAemSystemDAO.findByConfig(configOnce(turAemSourceContext)).map(TurAemSystem::isBooleanValue)
                .orElse(false);
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
                                    TurAemSourceContext turAemSourceContext,
                                    long start) {
        String type = Objects.requireNonNull(turAemSourceContext.getContentType());
        if (type.equals(CQ_PAGE)) {
            indexObject(aemObject, turCmsModel, targetAttrDefinitions, turAemSourceContext, start);
        } else if (type.equals(DAM_ASSET) && !StringUtils.isEmpty(turCmsModel.getSubType())) {
            if (turCmsModel.getSubType().equals(CONTENT_FRAGMENT) && aemObject.isContentFragment()) {
                aemObject.setDataPath(DATA_MASTER);
                indexObject(aemObject, turCmsModel, targetAttrDefinitions, turAemSourceContext, start);
            } else if (turCmsModel.getSubType().equals(STATIC_FILE)) {
                aemObject.setDataPath(METADATA);
                indexObject(aemObject, turCmsModel, targetAttrDefinitions, turAemSourceContext, start);
            }
        }
    }

    private void itemsProcessedStatus(long start) {
        if (processed.get() == 0) {
            currentPage.incrementAndGet();
            jCommander.getConsole().println("Processing %s item".formatted(
                    ordinal((currentPage.get() * pageSize) - pageSize + 1)));
        }
        if (processed.get() >= pageSize) {
            jCommander.getConsole().println(ITEMS_PROCESSED_MESSAGE.formatted(processed.get(),
                    System.currentTimeMillis() - start));
            processed = new AtomicInteger(0);
        } else {
            processed.incrementAndGet();
        }
    }

    private void deIndexObject(TurAemSourceContext turAemSourceContext) {
        turAemIndexingDAO.findContentsShouldBeDeIndexed(turAemSourceContext.getGroup(), deltaId).ifPresent(contents -> {
                    jCommander.getConsole().println("DeIndex Content that were removed...");
                    contents.forEach(content -> {
                        log.info("DeIndex {} object from {} group and {} delta",
                                content.getAemId(), turAemSourceContext.getGroup(), deltaId);
                        Map<String, Object> attributes = new HashMap<>();
                        attributes.put(AemHandlerConfiguration.ID_ATTRIBUTE, content.getAemId());
                        attributes.put(AemHandlerConfiguration.PROVIDER_ATTRIBUTE,
                                AemHandlerConfiguration.DEFAULT_PROVIDER);
                        sendJobToTuring(new TurSNJobItems(new TurSNJobItem(TurSNJobAction.DELETE,
                                Collections.singletonList(config.getDefaultSNSiteConfig().getName()),
                                content.getLocale(), attributes)), turAemSourceContext);
                    });
                    turAemIndexingDAO.deleteContentsWereDeIndexed(turAemSourceContext.getGroup(), deltaId);
                }
        );
    }

    private boolean objectNeedBeIndexed(AemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return (!StringUtils.isEmpty(aemObject.getPath()) &&
                !turAemIndexingDAO.existsByAemIdAndGroup(aemObject.getPath(), turAemSourceContext.getGroup()));
    }

    private boolean objectNeedBeReIndexed(AemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return !StringUtils.isEmpty(aemObject.getPath()) &&
                turAemIndexingDAO.existsByAemIdAndGroupAndDateNotEqual(aemObject.getPath(),
                        turAemSourceContext.getGroup(), TurAEMCommonsUtils.getDeltaDate(aemObject));
    }

    private void indexObject(AemObject aemObject, TurCmsModel turCmsModel,
                             List<TurSNAttributeSpec> turSNAttributeSpecList,
                             TurAemSourceContext turAemSourceContext,
                             Long start) {
        itemsProcessedStatus(start);

        if (!delivered || aemObject.isDelivered()) {
            final Locale locale = TurAEMCommonsUtils.getLocaleFromAemObject(turAemSourceContext, aemObject);
            if (objectNeedBeIndexed(aemObject, turAemSourceContext)) {
                if (!dryRun) {
                    createIndexingStatus(aemObject, locale, turAemSourceContext);
                }
                sendToTuringToBeIndexed(aemObject, turCmsModel, turSNAttributeSpecList, locale,
                        turAemSourceContext);
            } else {
                if (objectNeedBeReIndexed(aemObject, turAemSourceContext)) {
                    if (!dryRun) {
                        turAemIndexingDAO.findByAemIdAndGroup(aemObject.getPath(), turAemSourceContext.getGroup())
                                .ifPresent(turAemIndexingsList ->
                                        log.info("ReIndexed {} object ({}) from {} to {} and deltaId = {}",
                                                aemObject.getPath(), turAemSourceContext.getGroup(), turAemIndexingsList.getFirst().getDate(),
                                                TurAEMCommonsUtils.getDeltaDate(aemObject), deltaId));
                    }
                    sendToTuringToBeIndexed(aemObject, turCmsModel, turSNAttributeSpecList, locale,
                            turAemSourceContext);
                }
                if (!dryRun) {
                    updateIndexingStatus(aemObject, locale, turAemSourceContext);
                }
            }
        } else {
            log.info("Unpublished {} object ({}) deltaId = {}",
                    aemObject.getPath(), turAemSourceContext.getGroup(), deltaId);
        }
    }

    private void createIndexingStatus(AemObject aemObject, Locale locale, TurAemSourceContext turAemSourceContext) {
        turAemIndexingDAO.save(createTurAemIndexing(aemObject, locale, turAemSourceContext));
        log.info("Created status: {} object ({}) and deltaId = {}", aemObject.getPath(), turAemSourceContext.getGroup(), deltaId);
    }

    private void updateIndexingStatus(AemObject aemObject, Locale locale, TurAemSourceContext turAemSourceContext) {
        turAemIndexingDAO.findByAemIdAndGroup(aemObject.getPath(), turAemSourceContext.getGroup())
                .filter(turAemIndexingList -> !turAemIndexingList.isEmpty())
                .ifPresent(turAemIndexingList -> {
                    if (turAemIndexingList.size() > 1) {
                        turAemIndexingDAO.deleteByAemIdAndGroup(aemObject.getPath(), turAemSourceContext.getGroup());
                        log.info("Removed duplicated status {} object ({})",
                                aemObject.getPath(), turAemSourceContext.getGroup());
                        turAemIndexingDAO.save(createTurAemIndexing(aemObject, locale, turAemSourceContext));
                        log.info("Recreated status {} object ({}) and deltaId = {}",
                                aemObject.getPath(), turAemSourceContext.getGroup(), deltaId);
                    } else {
                        turAemIndexingDAO.update(turAemIndexingList.getFirst()
                                .setDate(TurAEMCommonsUtils.getDeltaDate(aemObject))
                                .setDeltaId(deltaId));
                        log.info("Updated status {} object ({}) deltaId = {}",
                                aemObject.getPath(), turAemSourceContext.getGroup(), deltaId);
                    }
                });
    }

    private TurAemIndexing createTurAemIndexing(AemObject aemObject, Locale locale, TurAemSourceContext turAemSourceContext) {
        return new TurAemIndexing()
                .setAemId(aemObject.getPath())
                .setIndexGroup(turAemSourceContext.getGroup())
                .setDate(TurAEMCommonsUtils.getDeltaDate(aemObject))
                .setDeltaId(deltaId)
                .setOnce(isOnceConfig(aemObject.getPath()))
                .setLocale(locale);
    }

    private void sendToTuringToBeIndexed(AemObject aemObject, TurCmsModel turCmsModel,
                                         List<TurSNAttributeSpec> turSNAttributeSpecList, Locale locale,
                                         TurAemSourceContext turAemSourceContext) {
        TurAEMAttrProcess turAEMAttrProcess = new TurAEMAttrProcess();
        TurCmsTargetAttrValueMap turCmsTargetAttrValueMap = turAEMAttrProcess
                .prepareAttributeDefs(aemObject, turCmsContentDefinitionProcess, turSNAttributeSpecList,
                        turAemSourceContext);
        turCmsTargetAttrValueMap.merge(TurAEMCommonsUtils.runCustomClassFromContentType(turCmsModel, aemObject, turAemSourceContext));
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
        sendJobToTuring(new TurSNJobItems(new TurSNJobItem(TurSNJobAction.CREATE, locale,
                Collections.singletonList(config.getDefaultSNSiteConfig().getName()), TurAEMCommonsUtils.castSpecToJobSpec(
                TurAEMCommonsUtils.getDefinitionFromModel(turSNAttributeSpecList, attributes)),
                attributes)), turAemSourceContext);
    }

    private void sendJobToTuring(TurSNJobItems turSNJobItems, TurAemSourceContext turAemSourceContext) {
        showOutput(turSNJobItems);
        if (!dryRun) {
            turSNJobItems.getTuringDocuments().stream().findFirst().ifPresent(document ->
                    log.info("Send {} object job ({}) to Turing",
                            document.getAttributes().get(ID), turAemSourceContext.getGroup()));
            if (!TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(config.getTuringURL(), config.getDefaultSNSiteConfig().getName(),
                            new TurApiKeyCredentials(config.getApiKey())),
                    false)) {
                throw new TurRuntimeException("Import Job Failed");
            }
        }
    }

    private void showOutput(TurSNJobItems turSNJobItems) {
        if (showOutput) try {
            jCommander.getConsole().println(new ObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(turSNJobItems));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }
    }
}
