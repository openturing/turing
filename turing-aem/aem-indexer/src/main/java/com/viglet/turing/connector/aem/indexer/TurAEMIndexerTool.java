package com.viglet.turing.connector.aem.indexer;

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
import com.viglet.turing.connector.cms.beans.*;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.config.TurSNSiteConfig;
import com.viglet.turing.connector.cms.mappers.CTDMappings;
import com.viglet.turing.connector.cms.mappers.MappingDefinitions;
import com.viglet.turing.connector.cms.mappers.MappingDefinitionsProcess;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.jackrabbit.commons.JcrUtils;
import org.json.JSONObject;

import javax.jcr.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

import static org.apache.jackrabbit.JcrConstants.JCR_CONTENT;
import static org.apache.jackrabbit.JcrConstants.JCR_PRIMARYTYPE;

@Slf4j
@Getter
public class TurAEMIndexerTool {
    public static final String JCR_TITLE = "jcr:title";
    public static final String CONTENT_FRAGMENT = "content-fragment";
    private static final JCommander jCommander = new JCommander();
    public static final String STATIC_FILE = "static-file";
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

    @Parameter(names = {"--site-path", "-s"}, description = "AEM site path.", required = false)
    private String sitePath = "/content/we-retail";

    @Parameter(names = "--delivered", description = "Publish delivery or author site", help = true)
    private boolean delivered = false;

    @Parameter(names = {"--page-size",
            "-z"}, description = "The page size. After processing a page the processed count is written to an offset file."
            + " This helps the indexer to resume from that page even after failure. ")
    private int pageSize = 50;

    @Parameter(names = "--debug", description = "Change the log level to debug", help = true)
    private boolean debug = false;

    @Parameter(names = "--property", description = "Property file location path", help = true)
    private String propertyPath = "turing-aem.properties";

    @Parameter(names = "--mode", description = "Mode connection type", converter = TurAemModeEnumConverter.class)
    private TurAemMode mode = TurAemMode.JCR;

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

    public static void main(String... argv) {
        TurAEMIndexerTool turAEMIndexerTool = new TurAEMIndexerTool();
        jCommander.addObject(turAEMIndexerTool);
        try {
            jCommander.parse(argv);

            //   if (turAEMIndexerTool.debug) {
            //     ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.
            //            getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            //   root.setLevel(Level.DEBUG);
            // }

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
        // createPersistence();

        config = new AemHandlerConfiguration(propertyPath);
        try {
            this.getRead();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void createPersistence() {
        String createTableSQL = "CREATE TABLE indexing (" +
                "id  VARCHAR(255) PRIMARY KEY," +
                "date DATE," +
                "status VARCHAR(255));";

        try (Connection connection = DriverManager.
                getConnection("jdbc:h2:./store/db/turing-aem-db", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void getRead() {

        switch (mode) {
            case JSON:
                getNodesFromJson();
                break;
            case JCR:
                getNodesFromJcr();
                break;
        }

    }

    private void getNodesFromJson() {
        if (contentType != null) {
            if (isCTDIntoMapping(contentType, config)) {
                jsonByContentType();
            } else {
                jCommander.getConsole()
                        .println(String.format("%s type is not configured in CTD Mapping XML file.", contentType));
            }
        } else if (guidFilePath != null) {
            jsonByGuidList();
        }
    }

    private void jsonByGuidList() {
        ArrayList<String> contentInstances;
        try (FileReader fr = new FileReader(guidFilePath); BufferedReader br = new BufferedReader(fr)) {
            contentInstances = br.lines().collect(Collectors.toCollection(ArrayList::new));
            if (!contentInstances.isEmpty())
                this.indexGUIDList(contentInstances);

        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void indexGUIDList(List<String> guids) {
        jCommander.getConsole().println(String.format("Processing a total of %d GUID Strings", guids.size()));
        guids.stream().filter(guid -> guid != null && !guid.isEmpty()).forEach(guid -> {
            start = System.currentTimeMillis();
            String jsonUrl = String.format("%s%s.infinity.json",
                    hostAndPort, guid);
            String json = getResponseBody(jsonUrl);
            JSONObject jsonObject = new JSONObject(json);
            siteName = jsonObject.getJSONObject(JCR_CONTENT).getString(JCR_TITLE);
            contentType = jsonObject.getString(JCR_PRIMARYTYPE);
            getNodeFromJson(guid, jsonObject);
            long elapsed = System.currentTimeMillis() - start;
            jCommander.getConsole().println(String.format("%d items processed in %dms", processed, elapsed));
        });

    }

    private void jsonByContentType() {
        start = System.currentTimeMillis();
        String jsonUrl = String.format("%s%s.infinity.json",
                hostAndPort, sitePath);
        String json = getResponseBody(jsonUrl);
        JSONObject jsonObject = new JSONObject(json);
        siteName = jsonObject.getJSONObject(JCR_CONTENT).getString(JCR_TITLE);
        getNodeFromJson(sitePath, jsonObject);
        long elapsed = System.currentTimeMillis() - start;
        jCommander.getConsole().println(String.format("%d items processed in %dms", processed, elapsed));
    }

    private void getNodesFromJcr() {
        if (isCTDIntoMapping(contentType, config)) {
            Session session = null;
            try {
                Repository repository = JcrUtils.getRepository(hostAndPort + "/crx/server");
                session = repository.login(new SimpleCredentials(username, password.toCharArray()));
                Node node = session.getNode(sitePath);
                AemSite aemSite = new AemSite(node);
                siteName = aemSite.getTitle();
                start = System.currentTimeMillis();
                getNodeFromJcr(node);
                long elapsed = System.currentTimeMillis() - start;
                jCommander.getConsole().println(String.format("%d items processed in %dms", processed, elapsed));
            } catch (RepositoryException e) {
                log.error(e.getMessage(), e);
            } finally {
                if (session != null) {
                    session.logout();
                }
            }
        } else {
            jCommander.getConsole()
                    .println(String.format("%s type is not configured in CTD Mapping XML file.", contentType));
        }
    }

    public static String ordinal(int i) {
        String[] suffixes = new String[]{"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th"};
        switch (i % 100) {
            case 11:
            case 12:
            case 13:
                return i + "th";
            default:
                return i + suffixes[i % 10];
        }
    }

    private String getResponseBody(String url) {
        HttpClient client = HttpClient.newBuilder()
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password.toCharArray());
                    }
                })
                .build();
        try {
            HttpRequest request = HttpRequest.newBuilder().GET().uri(new URI(url)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (URISyntaxException | IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void getNodeFromJson(String nodePath, JSONObject jsonObject) {
        if (jsonObject.getString(JCR_PRIMARYTYPE).equals(contentType)) {
            itemsProcessedStatus();
            CTDMappings ctdMappings = getCTDMappingMap(config).get(contentType);
            AemObject aemObject = new AemObject(nodePath, jsonObject);
            prepareIndexObject(ctdMappings, aemObject);
        }
        jsonObject.toMap().forEach((key, value) -> {
            if (!key.startsWith("jcr:")) {
                this.getNodeFromJson(nodePath + "/" + key, jsonObject.getJSONObject(key));
            }
        });
    }

    private void getNodeFromJcr(Node node) {
        try {
            if (node.hasNodes() && (node.getPath().startsWith("/content") || node.getPath().equals("/"))) {
                NodeIterator nodeIterator = node.getNodes();
                while (nodeIterator.hasNext()) {
                    Node nodeChild = nodeIterator.nextNode();
                    if (hasContentType(nodeChild, contentType) || contentType == null) {
                        itemsProcessedStatus();
                        CTDMappings ctdMappings = getCTDMappingMap(config).get(contentType);
                        AemObject aemObject = new AemObject(nodeChild);
                        if (!delivered || aemObject.isDelivered()) {
                            prepareIndexObject(ctdMappings, aemObject);
                        }
                    }
                    if (nodeChild.hasNodes()) {
                        getNodeFromJcr(nodeChild);
                    }
                }
            }
        } catch (RepositoryException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void prepareIndexObject(CTDMappings ctdMappings, AemObject aemObject) {
        try {
            final List<TurAttrDef> extAttributes = runCustomClassFromContentType(ctdMappings, aemObject);
            switch (Objects.requireNonNull(contentType)) {
                case CQ_PAGE:
                    indexObject(aemObject, extAttributes);
                    break;
                case DAM_ASSET:
                    if (ctdMappings.getSubType() != null) {
                        if (ctdMappings.getSubType().equals(CONTENT_FRAGMENT)
                                && aemObject.isContentFragment()) {
                            aemObject.setDataPath("data/master");
                            indexObject(aemObject, extAttributes);
                        } else if (ctdMappings.getSubType().equals(STATIC_FILE)) {
                            aemObject.setDataPath("metadata");
                            indexObject(aemObject, extAttributes);
                        }
                    }
                    break;
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException | RepositoryException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void itemsProcessedStatus() {
        if (processed == 0) {
            currentPage++;
            jCommander.getConsole().println(String.format("Processing %s item",
                    ordinal((currentPage * pageSize) - pageSize + 1)));
        }
        if (processed >= pageSize) {
            long elapsed = System.currentTimeMillis() - start;
            jCommander.getConsole()
                    .println(String.format("%d items processed in %dms", processed, elapsed));
            processed = 0;
            start = System.currentTimeMillis();
        } else {
            processed++;
        }
    }

    private List<TurAttrDef> runCustomClassFromContentType(CTDMappings ctdMappings, AemObject aemObject)
            throws InstantiationException, IllegalAccessException, InvocationTargetException,
            NoSuchMethodException, ClassNotFoundException {
        List<TurAttrDef> extAttributes = new ArrayList<>();
        if (ctdMappings.getClassName() != null) {
            Object extAttribute = Class.forName(ctdMappings.getClassName())
                    .getDeclaredConstructor().newInstance();
            extAttributes = ((ExtContentInterface) extAttribute)
                    .consume(aemObject, config, this);
        }
        return extAttributes;
    }

    private void indexObject(AemObject aemObject, List<TurAttrDef> extAttributes) throws RepositoryException {
        MappingDefinitions mappingDefinitions = MappingDefinitionsProcess.getMappingDefinitions(config);
        List<TurAttrDef> turAttrDefList = prepareAttributeDefs(aemObject, config, mappingDefinitions);
        turAttrDefList.addAll(extAttributes);
        TurSNSiteConfig turSNSiteConfig = config.getDefaultSNSiteConfig();
        Map<String, Object> attributes = new HashMap<>();
        String locale = config.getLocaleByPath(turSNSiteConfig.getName(), aemObject.getPath());
        final TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.CREATE,
                locale);
        turAttrDefList.forEach(turAttrDef -> {
            String attributeName = turAttrDef.getTagName();
            turAttrDef.getMultiValue().forEach(attributeValue -> {
                if (attributes.containsKey(attributeName)) {
                    addItemInExistingAttribute(attributeValue, attributes, attributeName);
                } else {
                    addFirstItemToAttribute(turAttrDef, attributeValue, attributes);
                }
            });
        });
        attributes.put("site", siteName);

        turSNJobItem.setAttributes(attributes);
        TurSNJobItems turSNJobItems = new TurSNJobItems();
        turSNJobItems.add(turSNJobItem);
        if (showOutput) {
            try {
                System.out.println(new ObjectMapper().writeValueAsString(turSNJobItems));
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
        }
        if (!dryRun) {
            try {
                TurSNServer turSNServer = new TurSNServer(new URL(config.getTuringURL()), turSNSiteConfig.getName(),
                        locale, config.getApiKey());
                TurSNJobUtils.importItems(turSNJobItems, turSNServer, false);
            } catch (MalformedURLException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    private static void addFirstItemToAttribute(TurAttrDef turAttrDef,
                                                String attributeValue,
                                                Map<String, Object> attributes) {
        attributes.put(turAttrDef.getTagName(), attributeValue);
    }

    public static boolean isCTDIntoMapping(String contentTypeName, IHandlerConfiguration config) {
        TurCTDMappingMap mappings = getCTDMappingMap(config);
        CTDMappings ctdMappings = mappings.get(contentTypeName);
        return ctdMappings != null;

    }

    public static TurCTDMappingMap getCTDMappingMap(IHandlerConfiguration config) {
        MappingDefinitions mappingDefinitions = MappingDefinitionsProcess.getMappingDefinitions(config);
        return mappingDefinitions.getMappingDefinitions();

    }

    private static List<TurAttrDef> prepareAttributeDefs(AemObject aemObject, IHandlerConfiguration config,
                                                         MappingDefinitions mappingDefinitions) {
        CTDMappings ctdMappings = mappingDefinitions.getMappingByContentType(aemObject.getType());
        List<TurAttrDef> attributesDefs = new ArrayList<>();
        if (ctdMappings == null) {
            log.error("Content Type not found: " + aemObject.getType());
        } else {
            for (String tag : ctdMappings.getTagList()) {

                if (log.isDebugEnabled()) {
                    log.debug(String.format("generateXMLToIndex: Tag: %s", tag));
                }
                for (TuringTag turingTag : ctdMappings.getTuringTagMap().get(tag)) {
                    if (tag != null && turingTag != null && turingTag.getTagName() != null) {
                        TurAttrDefContext turAttrDefContext = new TurAttrDefContext(aemObject, turingTag, config,
                                mappingDefinitions);
                        try {
                            List<TurAttrDef> attributeDefsXML = TurAEMAttrXML.attributeXML(turAttrDefContext);
                            // Unique
                            if (turingTag.isSrcUniqueValues()) {
                                attributesDefs.add(getTurAttrDefUnique(turingTag, attributeDefsXML));
                            } else {
                                attributesDefs.addAll(attributeDefsXML);
                            }
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                }
            }
        }
        return attributesDefs;
    }

    private static TurAttrDef getTurAttrDefUnique(TuringTag turingTag, List<TurAttrDef> attributeDefsXML) {
        TurMultiValue multiValue = new TurMultiValue();
        for (TurAttrDef turAttrDef : attributeDefsXML) {
            for (String singleValue : turAttrDef.getMultiValue()) {
                if (!multiValue.contains(singleValue)) {
                    multiValue.add(singleValue);
                }
            }
        }
        return new TurAttrDef(turingTag.getTagName(), multiValue);
    }

    private static void addItemInExistingAttribute(String attributeValue,
                                                   Map<String, Object> attributes,
                                                   String attributeName) {
        if (attributes.get(attributeName) instanceof ArrayList) {
            addItemToArray(attributes, attributeName, attributeValue);
        } else {
            convertAttributeSingleValueToArray(attributes, attributeName, attributeValue);
        }
    }

    private static void convertAttributeSingleValueToArray(Map<String, Object> attributes, String attributeName, String attributeValue) {
        List<Object> attributeValues = new ArrayList<>();
        attributeValues.add(attributes.get(attributeName));
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);
    }

    private static void addItemToArray(Map<String, Object> attributes, String attributeName, String attributeValue) {
        @SuppressWarnings("unchecked")
        List<Object> attributeValues = (List<Object>) attributes.get(attributeName);
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);
    }

    private static boolean hasContentType(Node nodeChild, String primaryType)
            throws RepositoryException {
        return primaryType != null && nodeChild.getProperty(JCR_PRIMARYTYPE).getString().equals(primaryType);
    }
}
