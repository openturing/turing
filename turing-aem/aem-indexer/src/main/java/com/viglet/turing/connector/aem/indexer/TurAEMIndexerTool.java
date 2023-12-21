package com.viglet.turing.connector.aem.indexer;

import ch.qos.logback.classic.Level;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.connector.aem.indexer.conf.AemHandlerConfiguration;
import com.viglet.turing.connector.aem.indexer.ext.ExtContentInterface;
import com.viglet.turing.connector.aem.indexer.persistence.TurAemIndexing;
import com.viglet.turing.connector.aem.indexer.persistence.TurAemIndexingDAO;
import com.viglet.turing.connector.cms.beans.*;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.config.TurSNSiteConfig;
import com.viglet.turing.connector.cms.mappers.CTDMappings;
import com.viglet.turing.connector.cms.mappers.MappingDefinitions;
import com.viglet.turing.connector.cms.mappers.MappingDefinitionsProcess;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.util.*;
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
    @Parameter(names = {"--host",
            "-h"}, description = "The host on which Content Management server is installed.", required = true)
    private String hostAndPort = null;
    @Parameter(names = {"--username",
            "-u"}, description = "A username to log in to the Content Management Server.", required = true)
    private String username = null;
    @Parameter(names = {"--password", "-p"}, description = "The password for the user name.", required = true)
    private String password = null;
    @Parameter(names = {"--all", "-a"}, description = "Index all instances of all content types and object types.")
    private boolean allObjectTypes = false;
    @Parameter(names = {"--content-type",
            "-c"}, description = "The XML name of the content type or object type whose instances are to be indexed.")
    private String contentType = null;
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
            log.info("Error: " + e.getLocalizedMessage());
            jCommander.usage();
        }
    }

    private void run() {
        config = new AemHandlerConfiguration(propertyPath);
        try {
            this.getNodesFromJson();
            if (!dryRun) deIndexObject();
            turAemIndexingDAO.close();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private void getNodesFromJson() {
        if (!StringUtils.isEmpty(contentType)) {
            if (isCTDIntoMapping(contentType, config)) {
                jsonByContentType();
            } else {
                jCommander.getConsole()
                        .println(String.format("%s type is not configured in CTD Mapping XML file.", contentType));
            }
        } else if (!StringUtils.isEmpty(guidFilePath)) {
            jsonByGuidList();
        }
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
        jCommander.getConsole().println(String.format("Processing a total of %d GUID Strings", guids.size()));
        guids.stream().filter(guid -> !StringUtils.isEmpty(guid)).forEach(guid -> {
            start = System.currentTimeMillis();
            final JSONObject jsonObject = TurAemUtils.getInfinityJson(guid, hostAndPort, username, password);
            rootPaths.forEach(rootPath -> {
                siteName = TurAemUtils.getInfinityJson(rootPath, hostAndPort, username, password)
                        .getJSONObject(JCR_CONTENT).getString(JCR_TITLE);
                contentType = jsonObject.getString(JCR_PRIMARY_TYPE);
                getNodeFromJson(guid, jsonObject);
                long elapsed = System.currentTimeMillis() - start;
                jCommander.getConsole().println(String.format("%d items processed in %dms", processed, elapsed));
            });

        });

    }

    private void jsonByContentType() {
        rootPaths.forEach(rootPath -> {
            JSONObject jsonSite = TurAemUtils.getInfinityJson(rootPath, hostAndPort, username, password);
            start = System.currentTimeMillis();
            if (jsonSite.has(JCR_CONTENT) && jsonSite.getJSONObject(JCR_CONTENT).has(JCR_TITLE)) {
                siteName = jsonSite.getJSONObject(JCR_CONTENT).getString(JCR_TITLE);
            } else {
                log.error(String.format("No site name the %s root path (%s)", rootPath, group));
            }
            getNodeFromJson(rootPath, jsonSite);
            jCommander.getConsole().println(String.format("%d items processed in %dms", processed,
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

    private void getNodeFromJson(String nodePath, JSONObject jsonObject) {
        if (jsonObject.has(JCR_PRIMARY_TYPE) && jsonObject.getString(JCR_PRIMARY_TYPE).equals(contentType)) {
            itemsProcessedStatus();
            prepareIndexObject(getCTDMappingMap(config).get(contentType), new AemObject(nodePath, jsonObject));
        }
        getChildrenFromJson(nodePath, jsonObject);
    }

    private void getChildrenFromJson(String nodePath, JSONObject jsonObject) {
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith(JCR)) {
                String nodePathChild = nodePath + "/" + key;
                if (isAemObjectJsonFull(jsonObject, key)) {
                    getNodeFromJson(nodePathChild,
                            jsonObject.getJSONObject(key));
                } else {
                    getNodeFromJson(nodePathChild,
                            TurAemUtils.getInfinityJson(nodePathChild, hostAndPort, username, password));
                }
            }
        });
    }

    private static boolean isAemObjectJsonFull(JSONObject jsonObject, String key) {
        return jsonObject.has(key) && jsonObject.getJSONObject(key).has(JCR_CONTENT);
    }

    private void prepareIndexObject(CTDMappings ctdMappings, AemObject aemObject) {
        final List<TurAttrDef> extAttributes = runCustomClassFromContentType(ctdMappings, aemObject);
        switch (Objects.requireNonNull(contentType)) {
            case CQ_PAGE:
                indexObject(aemObject, extAttributes);
                break;
            case DAM_ASSET:
                if (!StringUtils.isEmpty(ctdMappings.getSubType())) {
                    if (ctdMappings.getSubType().equals(CONTENT_FRAGMENT)
                            && aemObject.isContentFragment()) {
                        aemObject.setDataPath(DATA_MASTER);
                        indexObject(aemObject, extAttributes);
                    } else if (ctdMappings.getSubType().equals(STATIC_FILE)) {
                        aemObject.setDataPath(METADATA);
                        indexObject(aemObject, extAttributes);
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
            jCommander.getConsole()
                    .println(String.format("%d items processed in %dms", processed, System.currentTimeMillis() - start));
            processed = 0;
            start = System.currentTimeMillis();
        } else {
            processed++;
        }
    }

    private List<TurAttrDef> runCustomClassFromContentType(CTDMappings ctdMappings, AemObject aemObject) {
        try {
            return !StringUtils.isEmpty(ctdMappings.getClassName()) ?
                    ((ExtContentInterface) Class.forName(ctdMappings.getClassName())
                            .getDeclaredConstructor().newInstance())
                            .consume(aemObject, config, this) :
                    Collections.emptyList();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        return Collections.emptyList();
    }

    private void deIndexObject() {
        turAemIndexingDAO.findContentsShouldBeDeIndexed(group, deltaId).ifPresent(contents -> {
                    System.out.println("DeIndex Content that were removed...");
                    contents.forEach(content -> {
                        log.info(String.format("deIndex %s object from %s group and %s delta", content.getAemId(), group, deltaId));
                        Map<String, Object> attributes = new HashMap<>();
                        attributes.put(AemHandlerConfiguration.ID_ATTRIBUTE, content.getAemId());
                        attributes.put(AemHandlerConfiguration.PROVIDER_ATTRIBUTE, AemHandlerConfiguration.DEFAULT_PROVIDER);
                        sendJobToTuring(new TurSNJobItems(new TurSNJobItem(TurSNJobAction.DELETE,
                                LocaleUtils.toLocale(content.getLocale()), attributes)));
                    });
                    turAemIndexingDAO.deleteContentsWereDeIndexed(group, deltaId);
                }
        );
    }

    private void indexObject(AemObject aemObject, List<TurAttrDef> extAttributes) {
        if (dryRun || objectNeedBeIndexed(aemObject)) {
            Locale locale = LocaleUtils.toLocale(config.getLocaleByPath(config.getDefaultSNSiteConfig().getName(),
                    aemObject.getPath()));
            if (!dryRun) {
                turAemIndexingDAO.save(new TurAemIndexing()
                        .setAemId(aemObject.getPath())
                        .setIndexGroup(group)
                        .setDate(aemObject.getLastModified().getTime())
                        .setDeltaId(deltaId)
                        .setLocale(locale.toLanguageTag()));
                log.info(String.format("Created %s object (%s)", aemObject.getPath(), group));
            }
            List<TurAttrDef> turAttrDefList = prepareAttributeDefs(aemObject, config,
                    MappingDefinitionsProcess.getMappingDefinitions(config,
                            Paths.get(propertyPath).toAbsolutePath().getParent()));
            turAttrDefList.addAll(extAttributes);
            Map<String, Object> attributes = new HashMap<>();
            attributes.put(SITE, siteName);
            turAttrDefList.stream().filter(turAttrDef -> !CollectionUtils.isEmpty(turAttrDef.getMultiValue()))
                    .forEach(turAttrDef -> {
                        String attributeName = turAttrDef.getTagName();
                        turAttrDef.getMultiValue().forEach(attributeValue -> {
                            if (attributes.containsKey(attributeName)) {
                                addItemInExistingAttribute(attributeValue, attributes, attributeName);
                            } else {
                                addFirstItemToAttribute(turAttrDef, attributeValue, attributes);
                            }
                        });
                    });
            sendJobToTuring(new TurSNJobItems(new TurSNJobItem(TurSNJobAction.CREATE,
                    locale, attributes)));
        } else if (!dryRun) {
            turAemIndexingDAO.findByAemIdAndGroup(aemObject.getPath(), group).ifPresent(
                    turAemIndexing -> {
                        turAemIndexingDAO.update(turAemIndexing
                                .setDeltaId(deltaId));
                        log.info(String.format("Updated %s object (%s)", aemObject.getPath(), group));
                    });
        }
    }

    private boolean objectNeedBeIndexed(AemObject aemObject) {
        return (!StringUtils.isEmpty(aemObject.getPath()) && aemObject.getLastModified() != null &&
                !turAemIndexingDAO.existsByAemIdAndDateAndGroup(aemObject.getPath(),
                        aemObject.getLastModified().getTime(), group));
    }

    private void sendJobToTuring(TurSNJobItems turSNJobItems) {
        TurSNSiteConfig turSNSiteConfig = config.getDefaultSNSiteConfig();
        showOutput(turSNJobItems);
        if (!dryRun) {
            turSNJobItems.getTuringDocuments().stream().findFirst().ifPresent(document ->
                    log.info(String.format("Send %s object job (%s) to Turing", document.getAttributes().get("id"), group)));
            TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(config.getTuringURL(), turSNSiteConfig.getName(),
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

    private void addFirstItemToAttribute(TurAttrDef turAttrDef,
                                         String attributeValue,
                                         Map<String, Object> attributes) {
        attributes.put(turAttrDef.getTagName(), attributeValue);
    }

    public boolean isCTDIntoMapping(String contentTypeName, IHandlerConfiguration config) {
        return getCTDMappingMap(config).get(contentTypeName) != null;
    }

    public TurCTDMappingMap getCTDMappingMap(IHandlerConfiguration config) {
        return MappingDefinitionsProcess.getMappingDefinitions(config,
                Paths.get(propertyPath).toAbsolutePath().getParent()).getMappingDefinitions();

    }

    private List<TurAttrDef> prepareAttributeDefs(AemObject aemObject, IHandlerConfiguration config,
                                                  MappingDefinitions mappingDefinitions) {
        return Optional.ofNullable(mappingDefinitions.getMappingByContentType(aemObject.getType()))
                .map(ctdMappings -> {
                    List<TurAttrDef> attributesDefs = new ArrayList<>();
                    ctdMappings.getTagList().stream()
                            .filter(Objects::nonNull).forEach(tag -> {
                                log.debug(String.format("generateXMLToIndex: Tag: %s", tag));
                                ctdMappings.getTuringTagMap()
                                        .get(tag)
                                        .stream()
                                        .filter(turingTag -> ObjectUtils.allNotNull(turingTag, turingTag.getTagName()))
                                        .forEach(turingTag -> {
                                            try {
                                                List<TurAttrDef> attributeDefsXML = TurAEMAttrXML.attributeXML(
                                                        new TurAttrDefContext(aemObject, turingTag, config,
                                                                mappingDefinitions), this);
                                                if (turingTag.isSrcUniqueValues()) {
                                                    attributesDefs.add(getTurAttrDefUnique(turingTag, attributeDefsXML));
                                                } else {
                                                    attributesDefs.addAll(attributeDefsXML);
                                                }
                                            } catch (Exception e) {
                                                log.error(e.getMessage(), e);
                                            }
                                        });
                            });
                    return attributesDefs;
                }).orElseGet(() -> {
                    log.error("Content Type not found: " + aemObject.getType());
                    return Collections.emptyList();
                });
    }

    private static TurAttrDef getTurAttrDefUnique(TuringTag turingTag, List<TurAttrDef> attributeDefsXML) {
        TurMultiValue multiValue = new TurMultiValue();
        attributeDefsXML.stream().flatMap(turAttrDef ->
                turAttrDef.getMultiValue().stream()).distinct().forEach(multiValue::add);
        return new TurAttrDef(turingTag.getTagName(), multiValue);
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
