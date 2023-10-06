package com.viglet.turing.connector.aem.indexer;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.ValueFormatException;

import org.apache.jackrabbit.commons.JcrUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.credentials.TurUsernamePasswordCredentials;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.connector.aem.indexer.conf.AemHandlerConfiguration;
import com.viglet.turing.connector.cms.beans.TurAttrDef;
import com.viglet.turing.connector.cms.beans.TurAttrDefContext;
import com.viglet.turing.connector.cms.beans.TurCTDMappingMap;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.config.TurSNSiteConfig;
import com.viglet.turing.connector.cms.mappers.CTDMappings;
import com.viglet.turing.connector.cms.mappers.MappingDefinitions;
import com.viglet.turing.connector.cms.mappers.MappingDefinitionsProcess;

public class TurAEMIndexerTool {

	private static JCommander jCommander = new JCommander();
	private static Logger logger = LoggerFactory.getLogger(TurAEMIndexerTool.class);

	@Parameter(names = { "--host",
			"-h" }, description = "The host on which Content Management server is installed.", required = true)
	private String hostAndPort = null;

	@Parameter(names = { "--username",
			"-u" }, description = "A username to log in to the Content Management Server.", required = true)
	private String username = null;

	@Parameter(names = { "--password", "-p" }, description = "The password for the user name.", required = true)
	private String password = null;

	@Parameter(names = { "--working-dir",
			"-w" }, description = "The working directory where the vgncfg.properties file is located.", required = false)
	private String workingDir = null;

	@Parameter(names = { "--all", "-a" }, description = "Index all instances of all content types and object types.")
	private boolean allObjectTypes = false;

	@Parameter(names = { "--content-type",
			"-c" }, description = "The XML name of the content type or object type whose instances are to be indexed.")
	private String contentType = null;

	@Parameter(names = { "--guids",
			"-g" }, description = "The path to a file containing the GUID(s) of content instances or static files to be indexed.")
	private String guidFilePath = null;

	@Parameter(names = { "--sitePath", "-s" }, description = "AEM site path.", required = false)
	private String sitePath = "/content/we-retail";

	@Parameter(names = { "--page-size",
			"-z" }, description = "The page size. After processing a page the processed count is written to an offset file."
					+ " This helps the indexer to resume from that page even after failure. ")
	private int pageSize = 50;

	@Parameter(names = "--debug", description = "Change the log level to debug", help = true)
	private boolean debug = false;

	@Parameter(names = "--help", description = "Print usage instructions", help = true)
	private boolean help = false;

	private static String CQ_PAGE = "cq:Page";
	private static int processed = 0;
	private static int currentPage = 0;
	private static long start;
	AemHandlerConfiguration config = new AemHandlerConfiguration();
	private String siteName;

	public static void main(String... argv) {
		TurAEMIndexerTool turAEMIndexerTool = new TurAEMIndexerTool();

		jCommander.addObject(turAEMIndexerTool);

		try {
			jCommander.parse(argv);
			if (turAEMIndexerTool.help) {
				jCommander.usage();
				return;
			}
			jCommander.getConsole().println("Viglet Turing AEM Indexer Tool.");

			turAEMIndexerTool.run();
		} catch (ParameterException e) {
			logger.info("Error: " + e.getLocalizedMessage());
			jCommander.usage();
		}
	}

	private void run() {
		try {
			this.getRead();
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	public void getRead() throws Exception {
		if (isCTDIntoMapping(contentType, config)) {
			int totalPages = 0;
			Repository repository = JcrUtils.getRepository(hostAndPort);
			Session session = repository.login(new SimpleCredentials(username, password.toCharArray()));
			try {
				Node node = session.getNode(sitePath);
				AemSite aemSite = new AemSite(node);
				siteName = aemSite.getTitle();
				start = System.currentTimeMillis();
				getNode(node, totalPages);
				long elapsed = System.currentTimeMillis() - start;
				jCommander.getConsole().println(String.format("%d items processed in %dms", processed, elapsed));
			} finally {
				session.logout();
			}
		} else {
			jCommander.getConsole()
					.println(String.format("%s type is not configured in CTD Mapping XML file.", contentType));
		}
	}

	public static String ordinal(int i) {
		String[] suffixes = new String[] { "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th" };
		switch (i % 100) {
		case 11:
		case 12:
		case 13:
			return i + "th";
		default:
			return i + suffixes[i % 10];

		}
	}

	private void getNode(Node node, int totalPages) {

		try {

			if (node.hasNodes() && (node.getPath().startsWith("/content") || node.getPath().equals("/"))) {
				NodeIterator nodeIterator = node.getNodes();
				while (nodeIterator.hasNext()) {

					Node nodeChild = nodeIterator.nextNode();
					if (hasContentType(nodeChild, contentType) || contentType == null) {
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
						if (contentType != null && contentType.equals(CQ_PAGE)) {
							indexPage(new AemPage(nodeChild));
						} else {
							new AemObject(nodeChild);
						}
					}
					if (nodeChild.hasNodes()) {
						getNode(nodeChild, totalPages);
					}
				}
			}

		} catch (RepositoryException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public static boolean isCTDIntoMapping(String contentTypeName, IHandlerConfiguration config) {
		MappingDefinitions mappingDefinitions = MappingDefinitionsProcess.getMappingDefinitions(config);
		TurCTDMappingMap mappings = mappingDefinitions.getMappingDefinitions();
		CTDMappings ctdMappings = mappings.get(contentTypeName);
		return ctdMappings != null;

	}

	private static List<TurAttrDef> prepareAttributeDefs(AemObject aemObject, IHandlerConfiguration config,
			MappingDefinitions mappingDefinitions, String contentType) {
		CTDMappings ctdMappings = mappingDefinitions.getMappingByContentType(contentType);
		List<TurAttrDef> attributesDefs = new ArrayList<>();

		for (String tag : ctdMappings.getTagList()) {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("generateXMLToIndex: Tag: %s", tag));
			}
			for (TuringTag turingTag : ctdMappings.getTuringTagMap().get(tag)) {
				if (tag != null && turingTag != null && turingTag.getTagName() != null) {

					TurAttrDefContext turAttrDefContext = new TurAttrDefContext(aemObject, turingTag, config,
							mappingDefinitions);
					try {
						List<TurAttrDef> attributeDefsXML = TurAEMAttrXML.attributeXML(turAttrDefContext);
						// Unique
						if (turingTag.isSrcUniqueValues()) {

							TurMultiValue multiValue = new TurMultiValue();
							for (TurAttrDef turAttrDef : attributeDefsXML) {
								for (String singleValue : turAttrDef.getMultiValue()) {
									if (!multiValue.contains(singleValue)) {
										multiValue.add(singleValue);
									}
								}
							}
							TurAttrDef turAttrDefUnique = new TurAttrDef(turingTag.getTagName(), multiValue);
							attributesDefs.add(turAttrDefUnique);
						} else {
							attributesDefs.addAll(attributeDefsXML);
						}
					} catch (Exception e) {
						logger.error(e.getMessage(), e);
					}
				}
			}
		}
		return attributesDefs;
	}

	private void indexPage(AemPage aemPage) throws PathNotFoundException, RepositoryException, ValueFormatException {

		MappingDefinitions mappingDefinitions = MappingDefinitionsProcess.getMappingDefinitions(config);
		List<TurAttrDef> turAttrDefList = TurAEMIndexerTool.prepareAttributeDefs(aemPage, config, mappingDefinitions,
				"cq:Page");
		TurSNSiteConfig turSNSiteConfig = config.getDefaultSNSiteConfig();
		Map<String, Object> attributes = new HashMap<>();
		for (TurAttrDef turAttrDef : turAttrDefList) {
			turAttrDef.getMultiValue().forEach(value -> attributes.put(turAttrDef.getTagName(), value));
		}
		attributes.put("site", siteName);
		final TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.CREATE, turSNSiteConfig.getLocale());

		turSNJobItem.setAttributes(attributes);
		TurSNJobItems turSNJobItems = new TurSNJobItems();
		turSNJobItems.add(turSNJobItem);
		TurUsernamePasswordCredentials credentials = new TurUsernamePasswordCredentials(config.getLogin(),
				config.getPassword());
		TurSNServer turSNServer;
		try {
			turSNServer = new TurSNServer(new URL(config.getTuringURL()), turSNSiteConfig.getName(),
					turSNSiteConfig.getLocale(), credentials);
			TurSNJobUtils.importItems(turSNJobItems, turSNServer, false);
		} catch (MalformedURLException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private static boolean hasContentType(Node nodeChild, String primaryType)
			throws ValueFormatException, RepositoryException, PathNotFoundException {
		return primaryType != null && nodeChild.getProperty("jcr:primaryType").getString().equals(primaryType);
	}
}
