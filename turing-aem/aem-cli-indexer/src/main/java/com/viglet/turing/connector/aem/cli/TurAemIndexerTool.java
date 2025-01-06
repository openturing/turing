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

package com.viglet.turing.connector.aem.cli;

import ch.qos.logback.classic.Level;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.*;
import com.viglet.turing.commons.exception.TurRuntimeException;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.connector.aem.cli.conf.AemHandlerConfiguration;
import com.viglet.turing.connector.aem.cli.persistence.TurAemIndexing;
import com.viglet.turing.connector.aem.cli.persistence.TurAemIndexingDAO;
import com.viglet.turing.connector.aem.cli.persistence.TurAemSystem;
import com.viglet.turing.connector.aem.cli.persistence.TurAemSystemDAO;
import com.viglet.turing.connector.aem.commons.TurAemAttrProcess;
import com.viglet.turing.connector.aem.commons.TurAemCommonsUtils;
import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.bean.TurAemTargetAttrValueMap;
import com.viglet.turing.connector.aem.commons.config.IAemConfiguration;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.mappers.TurAemContentDefinitionProcess;
import com.viglet.turing.connector.aem.commons.mappers.TurAemModel;
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
import java.util.stream.Collectors;

@Slf4j
@Getter
public class TurAemIndexerTool {
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
    private IAemConfiguration config = null;
    private String siteName;
    private final String deltaId = UUID.randomUUID().toString();
    private final TurAemIndexingDAO turAemIndexingDAO = new TurAemIndexingDAO();
    private final TurAemSystemDAO turAemSystemDAO = new TurAemSystemDAO();
    private TurAemContentDefinitionProcess turAemContentDefinitionProcess;
    private AtomicInteger processed = new AtomicInteger(0);
    private AtomicInteger currentPage = new AtomicInteger(0);


    public static void main(String... argv) {
        jCommander.getConsole().println("Viglet Turing AEM Indexer Tool. " +
                TurAemIndexerTool.class.getPackage().getImplementationVersion());
        TurAemIndexerTool turAEMIndexerTool = new TurAemIndexerTool();
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
        turAemContentDefinitionProcess = new TurAemContentDefinitionProcess(config,
                Paths.get(propertyPath).toAbsolutePath().getParent());
        TurAemSourceContext turAemSourceContext = getTurAemSourceContext(config, this);
        try {
            if (reindex) {
                turAemIndexingDAO.deleteContentsToReindex(turAemSourceContext.getId());
            }
            if (reindexOnce) {
                turAemIndexingDAO.deleteContentsToReindexOnce(turAemSourceContext.getId());
            }
            this.getNodesFromJson(turAemSourceContext);
            if (!dryRun && !usingGuidParameter()) {
                deIndexObjects(turAemSourceContext);
                updateSystemOnce(turAemSourceContext);
            }
            turAemIndexingDAO.close();
            turAemSystemDAO.close();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private TurAemSourceContext getTurAemSourceContext(IAemConfiguration config,
                                                       TurAemIndexerTool turAEMIndexerTool) {
        TurAemSourceContext turAemSourceContext = TurAemSourceContext.builder()
                .id(config.getCmsGroup())
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
        if (log.isDebugEnabled()) {
            log.debug("TurAemSourceContext: {}", turAemSourceContext.toString());
        }
        return turAemSourceContext;
    }

    private void updateSystemOnce(TurAemSourceContext turAemSourceContext) {
        turAemSystemDAO.findByConfig(TurAemCommonsUtils.configOnce(turAemSourceContext))
                .ifPresentOrElse(turAemSystem -> {
                            turAemSystem.setBooleanValue(true);
                            turAemSystemDAO.update(turAemSystem);
                        },
                        () -> turAemSystemDAO.save(new TurAemSystem(TurAemCommonsUtils.configOnce(turAemSourceContext),
                                true)));
    }

    private void getNodesFromJson(TurAemSourceContext turAemSourceContext) {
        if (usingGuidParameter()) {
            byGuidList(turAemSourceContext);
        } else if (TurAemCommonsUtils.usingContentTypeParameter(turAemSourceContext)) {
            byContentTypeList(turAemSourceContext);
        }
    }

    private void byGuidList(TurAemSourceContext turAemSourceContext) {
        ArrayList<String> contentInstances;
        try (FileReader fr = new FileReader(guidFilePath);
             BufferedReader br = new BufferedReader(fr)) {
            contentInstances = br.lines().collect(Collectors.toCollection(ArrayList::new));
            if (!contentInstances.isEmpty())
                this.indexGuidList(contentInstances, turAemSourceContext);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void byContentTypeList(TurAemSourceContext turAemSourceContext) {
        turAemContentDefinitionProcess.findByNameFromModelWithDefinition(turAemSourceContext.getContentType())
                .ifPresentOrElse(turAemModel -> byContentType(turAemSourceContext),
                        () -> jCommander.getConsole()
                                .println("%s type is not configured in CTD Mapping file.".formatted(
                                        turAemSourceContext.getContentType())));
    }

    private void byContentType(TurAemSourceContext turAemSourceContext) {
        TurAemCommonsUtils.getInfinityJson(turAemSourceContext.getRootPath(), turAemSourceContext, false)
                .ifPresent(infinityJson -> {
                    long start = System.currentTimeMillis();
                    TurAemCommonsUtils.getSiteName(turAemSourceContext, infinityJson).ifPresent(s -> siteName = s);
                    getNodeFromJson(turAemSourceContext.getRootPath(), infinityJson, turAemSourceContext, start);
                    jCommander.getConsole().println(ITEMS_PROCESSED_MESSAGE.formatted(processed.get(),
                            System.currentTimeMillis() - start));
                });
    }

    private boolean usingGuidParameter() {
        return StringUtils.isNotBlank(guidFilePath);
    }


    private void indexGuidList(List<String> guids, TurAemSourceContext turAemSourceContext) {
        jCommander.getConsole().println("Processing a total of %d GUID Strings".formatted(guids.size()));
        guids.stream().filter(guid -> !StringUtils.isEmpty(guid)).forEach(guid -> {
            long start = System.currentTimeMillis();
            TurAemCommonsUtils.getInfinityJson(turAemSourceContext.getRootPath(), turAemSourceContext, false)
                    .flatMap(infinityJson -> TurAemCommonsUtils
                            .getSiteName(turAemSourceContext, infinityJson)).ifPresent(s -> siteName = s);
            TurAemCommonsUtils.getInfinityJson(guid, turAemSourceContext, false)
                    .ifPresent(infinityJson -> {
                        turAemSourceContext.setContentType(infinityJson.getString(JCR_PRIMARY_TYPE));
                        getNodeFromJson(guid, infinityJson, turAemSourceContext, start);
                        long elapsed = System.currentTimeMillis() - start;
                        jCommander.getConsole().println(ITEMS_PROCESSED_MESSAGE.formatted(processed.get(), elapsed));
                    });

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
        TurAemObject aemObject = new TurAemObject(nodePath, jsonObject);
        Optional.of(aemObject).ifPresentOrElse(o -> {
            if (TurAemCommonsUtils.isTypeEqualContentType(jsonObject, turAemSourceContext)) {
                turAemContentDefinitionProcess.findByNameFromModelWithDefinition(turAemSourceContext.getContentType())
                        .ifPresent(model ->
                                prepareIndexObject(model, new TurAemObject(nodePath, jsonObject),
                                        turAemContentDefinitionProcess.getTargetAttrDefinitions(), turAemSourceContext, start));
            }
        }, () -> log.info("AEM object ({}) is null deltaId = {}",
                turAemSourceContext.getId(), deltaId));
        getChildrenFromJson(nodePath, jsonObject, turAemSourceContext, start);
    }

    private void getChildrenFromJson(String nodePath, JSONObject jsonObject, TurAemSourceContext turAemSourceContext,
                                     long start) {
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith(JCR) && !key.startsWith(REP) && !key.startsWith(CQ)
                    && (turAemSourceContext.getSubType().equals(STATIC_FILE_SUB_TYPE)
                    || TurAemCommonsUtils.checkIfFileHasNotImageExtension(key))) {
                String nodePathChild = "%s/%s".formatted(nodePath, key);
                if (!isOnce(turAemSourceContext) || !TurAemCommonsUtils.isOnceConfig(nodePathChild, config)) {
                    TurAemCommonsUtils.getInfinityJson(nodePathChild, turAemSourceContext, false)
                            .ifPresent(infinityJson ->
                                    getNodeFromJson(nodePathChild, infinityJson, turAemSourceContext, start));
                }
            }
        });
    }

    private boolean isOnce(TurAemSourceContext turAemSourceContext) {
        return turAemSystemDAO.findByConfig(TurAemCommonsUtils.configOnce(turAemSourceContext))
                .map(TurAemSystem::isBooleanValue)
                .orElse(false);
    }

    private void prepareIndexObject(TurAemModel turAemModel, TurAemObject aemObject,
                                    List<TurSNAttributeSpec> targetAttrDefinitions,
                                    TurAemSourceContext turAemSourceContext,
                                    long start) {
        String type = Objects.requireNonNull(turAemSourceContext.getContentType());
        if (type.equals(CQ_PAGE)) {
            indexObject(aemObject, turAemModel, targetAttrDefinitions, turAemSourceContext, start);
        } else if (type.equals(DAM_ASSET) && !StringUtils.isEmpty(turAemModel.getSubType())) {
            if (turAemModel.getSubType().equals(CONTENT_FRAGMENT) && aemObject.isContentFragment()) {
                aemObject.setDataPath(DATA_MASTER);
                indexObject(aemObject, turAemModel, targetAttrDefinitions, turAemSourceContext, start);
            } else if (turAemModel.getSubType().equals(STATIC_FILE)) {
                aemObject.setDataPath(METADATA);
                indexObject(aemObject, turAemModel, targetAttrDefinitions, turAemSourceContext, start);
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

    private void deIndexObjects(TurAemSourceContext turAemSourceContext) {
        turAemIndexingDAO.findContentsShouldBeDeIndexed(turAemSourceContext.getId(), deltaId)
                .ifPresent(contents -> {
                            jCommander.getConsole().println("DeIndex Content that were removed...");
                            contents.forEach(content -> {
                                log.info("DeIndex {} object from {} group and {} delta",
                                        content.getAemId(), turAemSourceContext.getId(), deltaId);
                                Map<String, Object> attributes = new HashMap<>();
                                attributes.put(TurSNFieldName.ID, content.getAemId());
                                attributes.put(TurSNFieldName.SOURCE_APPS,
                                        IAemConfiguration.DEFAULT_PROVIDER);
                                sendJobToTuring(new TurSNJobItems(new TurSNJobItem(TurSNJobAction.DELETE,
                                        Collections.singletonList(config.getDefaultSNSiteConfig().getName()),
                                        content.getLocale(), attributes)), turAemSourceContext);
                            });
                            turAemIndexingDAO.deleteContentsWereDeIndexed(turAemSourceContext.getId(), deltaId);
                        }
                );
    }

    private boolean objectNeedBeIndexed(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return (!StringUtils.isEmpty(aemObject.getPath()) &&
                !turAemIndexingDAO.existsByAemIdAndGroup(aemObject.getPath(), turAemSourceContext.getId()));
    }

    private boolean objectNeedBeReIndexed(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return ddlNeedBeReIndexed(aemObject, turAemSourceContext, TurAemCommonsUtils.getDeltaDate(aemObject,
                turAemSourceContext, turAemContentDefinitionProcess));
    }

    private boolean ddlNeedBeReIndexed(TurAemObject aemObject, TurAemSourceContext turAemSourceContext,
                                       Date deltaDate) {
        return !StringUtils.isEmpty(aemObject.getPath()) &&
                turAemIndexingDAO.existsByAemIdAndGroupAndDateNotEqual(aemObject.getPath(),
                        turAemSourceContext.getId(), deltaDate);
    }

    private void indexObject(@NotNull TurAemObject aemObject, TurAemModel turAemModel,
                             List<TurSNAttributeSpec> turSNAttributeSpecList,
                             TurAemSourceContext turAemSourceContext,
                             Long start) {
        itemsProcessedStatus(start);
        if (!delivered || aemObject.isDelivered()) {
            final Locale locale = TurAemCommonsUtils.getLocaleFromAemObject(turAemSourceContext, aemObject);
            if (objectNeedBeIndexed(aemObject, turAemSourceContext)) {
                sendIndex(aemObject, turAemModel, turSNAttributeSpecList, turAemSourceContext, locale);
            } else {
                if (objectNeedBeReIndexed(aemObject, turAemSourceContext)) {
                    sendReindex(aemObject, turAemModel, turSNAttributeSpecList, turAemSourceContext, locale);
                }
                if (!dryRun) {
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
        if (!dryRun) {
            turAemIndexingDAO.findByAemIdAndGroup(aemObject.getPath(), turAemSourceContext.getId())
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
        if (!dryRun) {
            createIndexingStatus(aemObject, locale, turAemSourceContext);
        }
        sendToTuringToBeIndexed(aemObject, turAemModel, turSNAttributeSpecList, locale,
                turAemSourceContext);
    }

    private void createIndexingStatus(TurAemObject aemObject, Locale locale, TurAemSourceContext turAemSourceContext) {
        turAemIndexingDAO.save(createTurAemIndexing(aemObject, locale, turAemSourceContext));
        log.info("Created status: {} object ({}) and deltaId = {}", aemObject.getPath(),
                turAemSourceContext.getId(), deltaId);
    }

    private void updateIndexingStatus(TurAemObject aemObject, Locale locale, TurAemSourceContext turAemSourceContext) {
        turAemIndexingDAO.findByAemIdAndGroup(aemObject.getPath(), turAemSourceContext.getId())
                .filter(turAemIndexingList -> !turAemIndexingList.isEmpty())
                .ifPresent(turAemIndexingList -> {
                    if (turAemIndexingList.size() > 1) {
                        turAemIndexingDAO.deleteByAemIdAndGroup(aemObject.getPath(), turAemSourceContext.getId());
                        log.info("Removed duplicated status {} object ({})",
                                aemObject.getPath(), turAemSourceContext.getId());
                        turAemIndexingDAO.save(createTurAemIndexing(aemObject, locale, turAemSourceContext));
                        log.info("Recreated status {} object ({}) and deltaId = {}",
                                aemObject.getPath(), turAemSourceContext.getId(), deltaId);
                    } else {
                        turAemIndexingDAO.update(turAemIndexingList.getFirst()
                                .setDate(TurAemCommonsUtils.getDeltaDate(aemObject, turAemSourceContext,
                                        turAemContentDefinitionProcess))
                                .setDeltaId(deltaId));
                        log.info("Updated status {} object ({}) deltaId = {}",
                                aemObject.getPath(), turAemSourceContext.getId(), deltaId);
                    }
                });
    }

    private TurAemIndexing createTurAemIndexing(TurAemObject aemObject, Locale locale,
                                                TurAemSourceContext turAemSourceContext) {
        return new TurAemIndexing()
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
        sendJobToTuring(new TurSNJobItems(new TurSNJobItem(TurSNJobAction.CREATE,
                Collections.singletonList(config.getDefaultSNSiteConfig().getName()),
                locale,
                attributes,
                TurAemCommonsUtils.castSpecToJobSpec(
                        TurAemCommonsUtils.getDefinitionFromModel(turSNAttributeSpecList, attributes))
        )), turAemSourceContext);
    }

    private void sendJobToTuring(TurSNJobItems turSNJobItems, TurAemSourceContext turAemSourceContext) {
        showOutput(turSNJobItems);
        if (!dryRun) {
            turSNJobItems.getTuringDocuments().stream().findFirst()
                    .ifPresent(document ->
                            log.info("Send {} object job ({}) to Turing",
                                    document.getAttributes().get(ID), turAemSourceContext.getId()));
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
