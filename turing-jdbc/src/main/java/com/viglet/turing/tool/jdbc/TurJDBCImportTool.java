/*
 * Copyright (C) 2016-2022 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.tool.jdbc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.credentials.TurUsernamePasswordCredentials;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.tool.file.TurFileAttributes;
import com.viglet.turing.tool.impl.TurJDBCCustomImpl;
import com.viglet.turing.tool.jdbc.format.TurFormatValue;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

/**
 * Class that can be used to bootstrap and launch JDBC Import Tool
 *
 * @author Alexandre Oliveira
 * @since 0.3.0
 *
 **/
public class TurJDBCImportTool {
	static final Logger logger = LogManager.getLogger(TurJDBCImportTool.class.getName());

	private static final long MEGA_BYTE = 1024L * 1024L;
	private static final String TYPE_ATTRIBUTE = "type";
	@Parameter(names = { "--deindex-before-importing" }, description = "Deindex before importing", arity = 1)
	private boolean deindexBeforeImporting = false;

	@Parameter(names = { "--max-content-size" }, description = "Maximum size that content can be indexed (megabytes)")
	private long maxContentMegaByteSize = 5;

	@Parameter(names = { "--driver", "-d" }, description = "Manually specify JDBC driver class to use", required = true)
	private String driver = null;

	@Parameter(names = { "--connect", "-c" }, description = "Specify JDBC connect string", required = true)
	private String connect = null;

	@Parameter(names = { "--query", "-q" }, description = "Import the results of statement", required = true)
	private String query = null;

	@Parameter(names = { "--site" }, description = "Specify the Semantic Navigation Site", required = true)
	private String site = null;

	@Parameter(names = { "--locale" }, description = "Specify the Semantic Navigation Site Locale", required = false)
	private String locale = "en_US";

	@Parameter(names = { "--server", "-s" }, description = "Viglet Turing Server")
	private String turingServer = "http://localhost:2700";

	@Parameter(names = { "--username", "-u" }, description = "Set authentication Turing username")
	private String turUsername = null;

	@Parameter(names = { "--password", "-p" }, description = "Set authentication Turing password")
	private String turPassword = null;

	@Parameter(names = { "--db-username" }, description = "Set authentication database username")
	private String dbUsername = null;

	@Parameter(names = { "--db-password" }, description = "Set authentication database password")
	private String dbPassword = null;

	@Parameter(names = { "--type", "-t" }, description = "Set Content Type name")
	public String type = "CONTENT_TYPE";

	@Parameter(names = { "--chunk", "-z" }, description = "Number of items to be sent to the queue")
	private int chunk = 100;

	@Parameter(names = { "--include-type-in-id", "-i" }, description = "Include Content Type name in Id", arity = 1)
	private boolean typeInId = false;

	@Parameter(names = { "--multi-valued-separator" }, description = "Multi Valued Separator")
	private String mvSeparator = ",";

	@Parameter(names = { "--multi-valued-field" }, description = "Multi Valued Fields")
	private String mvField = "";

	@Parameter(names = { "--remove-html-tags-field" }, description = "Remove HTML Tags into content of field")
	private String htmlField = "";

	@Parameter(names = "--file-path-field", description = "Field with File Path", help = true)
	private String filePathField = null;

	@Parameter(names = "--file-content-field", description = "Field that shows Content of File", help = true)
	private String fileContentField = null;

	@Parameter(names = "--file-size-field", description = "Field that shows Size of File in bytes", help = true)
	private String fileSizeField = null;

	@Parameter(names = "--class-name", description = "Customized Class to modified rows", help = true)
	private String customClassName = null;

	@Parameter(names = { "--show-output", "-o" }, description = "Show Output", arity = 1)
	private boolean showOutput = false;

	@Parameter(names = { "--encoding" }, description = "Encoding Source")
	private String encoding = "UTF-8";

	@Parameter(names = "--help", description = "Print usage instructions", help = true)
	private boolean help = false;

	private static TurFormatValue turFormatValue = null;

	public boolean isDeindexBeforeImporting() {
		return deindexBeforeImporting;
	}

	public long getMaxContentMegaByteSize() {
		return maxContentMegaByteSize;
	}

	public String getDriver() {
		return driver;
	}

	public String getConnect() {
		return connect;
	}

	public String getQuery() {
		return query;
	}

	public String getSite() {
		return site;
	}

	public String getTuringServer() {
		return turingServer;
	}

	public String getTurUsername() {
		return turUsername;
	}

	public void setTurUsername(String turUsername) {
		this.turUsername = turUsername;
	}

	public String getTurPassword() {
		return turPassword;
	}

	public void setTurPassword(String turPassword) {
		this.turPassword = turPassword;
	}

	public String getDbUsername() {
		return dbUsername;
	}

	public void setDbUsername(String dbUsername) {
		this.dbUsername = dbUsername;
	}

	public String getDbPassword() {
		return dbPassword;
	}

	public void setDbPassword(String dbPassword) {
		this.dbPassword = dbPassword;
	}

	public String getType() {
		return type;
	}

	public int getChunk() {
		return chunk;
	}

	public boolean isTypeInId() {
		return typeInId;
	}

	public String getMvSeparator() {
		return mvSeparator;
	}

	public String getMvField() {
		return mvField;
	}

	public String getHtmlField() {
		return htmlField;
	}

	public String getFilePathField() {
		return filePathField;
	}

	public String getFileContentField() {
		return fileContentField;
	}

	public String getFileSizeField() {
		return fileSizeField;
	}

	public String getCustomClassName() {
		return customClassName;
	}

	public boolean isShowOutput() {
		return showOutput;
	}

	public String getEncoding() {
		return encoding;
	}

	public boolean isHelp() {
		return help;
	}

	public static void main(String... argv) {

		TurJDBCImportTool main = new TurJDBCImportTool();
		JCommander jCommander = JCommander.newBuilder().addObject(main).build();
		try {
			jCommander.parse(argv);
			if (main.help) {
				jCommander.usage();
				return;
			}
			System.out.println("Viglet Turing JDBC Import Tool.");
			turFormatValue = new TurFormatValue(main);
			main.run();
		} catch (ParameterException e) {
			logger.info("Error: {}", e.getLocalizedMessage());
			jCommander.usage();
		}

	}

	public void run() {
		logger.info("driver: {}", driver);
		logger.info("connect: {}", connect);
		logger.info("query: {}", query);
		logger.info("username: {}", dbUsername);

		this.select();
	}

	private TurFileAttributes readFile(String filePath) {

		try {
			File file = new File(filePath);
			if (file.exists()) {
				try (InputStream inputStream = new FileInputStream(file)) {
					AutoDetectParser parser = new AutoDetectParser();
					// -1 = no limit of number of characters
					BodyContentHandler handler = new BodyContentHandler(-1);
					Metadata metadata = new Metadata();

					ParseContext pcontext = new ParseContext();

					parser.parse(inputStream, handler, metadata, pcontext);
					TurFileAttributes turFileAttributes = new TurFileAttributes();
					turFileAttributes.setContent(cleanTextContent(handler.toString()));
					turFileAttributes.setFile(file);
					turFileAttributes.setMetadata(metadata);

					return turFileAttributes;
				}
			} else {
				logger.info("File not exists: {}", filePath);
			}
		} catch (IOException | SAXException | TikaException e) {
			logger.error("readFile Exception", e);
		}

		return null;

	}

	private void select() {
		try {
			TurSNServer turSNServer = new TurSNServer(new URL(turingServer), this.site, this.locale,
					new TurUsernamePasswordCredentials(turUsername, turPassword));

			if (this.deindexBeforeImporting) {
				turSNServer.deleteItemsByType(type);
			}
			TurJDBCCustomImpl turJDBCCustomImpl = instantiateCustomClass();

			// Register JDBC driver
			Class.forName(driver);

			logger.info("Execute a query...");
			try (Connection conn = DriverManager.getConnection(connect, dbUsername, dbPassword);
					Statement stmt = conn.createStatement();
					ResultSet rs = stmt.executeQuery(query)) {
				TurChunkingJob turChunkingJob = new TurChunkingJob(chunk);
				while (rs.next()) {
					turChunkingJob.addItem(createJobItem(turJDBCCustomImpl, conn, rs));
					if (turChunkingJob.isChunkLimit()) {
						this.sendServer(turSNServer, turChunkingJob);
						turChunkingJob.newCicle();
					}
				}
				if (turChunkingJob.hasItemsLeft()) {
					this.sendServer(turSNServer, turChunkingJob);
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	private TurJDBCCustomImpl instantiateCustomClass() {
		TurJDBCCustomImpl turJDBCCustomImpl = null;
		if (customClassName != null) {
			try {
				turJDBCCustomImpl = (TurJDBCCustomImpl) Class.forName(customClassName).getDeclaredConstructor()
						.newInstance();
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
		return turJDBCCustomImpl;
	}

	private TurSNJobItem createJobItem(TurJDBCCustomImpl turJDBCCustomImpl, Connection conn, ResultSet rs)
			throws SQLException {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(TYPE_ATTRIBUTE, type);
		addDBFieldsAsAttributes(rs, attributes);
		addFileAttributes(attributes);
		attributes = modifyAttributesByCustomClass(turJDBCCustomImpl, conn, attributes);
		addMultiValuedAttributes(attributes);
		TurSNJobItem turSNJobItem = new TurSNJobItem();
		turSNJobItem.setTurSNJobAction(TurSNJobAction.CREATE);
		turSNJobItem.setAttributes(attributes);
		turSNJobItem.setLocale(locale);
		return turSNJobItem;
	}

	private Map<String, Object> modifyAttributesByCustomClass(TurJDBCCustomImpl turJDBCCustomImpl, Connection conn,
			Map<String, Object> attributes) {
		if (hasCustomClass(turJDBCCustomImpl))
			attributes = turJDBCCustomImpl.run(conn, attributes);
		return attributes;
	}

	private boolean hasCustomClass(TurJDBCCustomImpl turJDBCCustomImpl) {
		return customClassName != null && turJDBCCustomImpl != null;
	}

	private void addDBFieldsAsAttributes(ResultSet rs, Map<String, Object> attributes) throws SQLException {
		ResultSetMetaData rsmd = rs.getMetaData();
		for (int c = 1; c <= rsmd.getColumnCount(); c++) {
			String nameSensitve = rsmd.getColumnLabel(c);
			String className = rsmd.getColumnClassName(c);

			if (className.equals("java.lang.Integer")) {
				int intValue = rs.getInt(c);
				attributes.put(nameSensitve, turFormatValue.format(nameSensitve, Integer.toString(intValue)));
			} else if (className.equals("java.sql.Timestamp")) {
				TimeZone tz = TimeZone.getTimeZone("UTC");
				DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
				df.setTimeZone(tz);
				attributes.put(nameSensitve, turFormatValue.format(nameSensitve, df.format(rs.getDate(c))));
			} else {
				String strValue = rs.getString(c);
				attributes.put(nameSensitve, turFormatValue.format(nameSensitve, strValue));
			}
		}
	}

	private void addFileAttributes(Map<String, Object> attributes) {
		if (filePathField != null && attributes.containsKey(filePathField)) {
			TurFileAttributes turFileAttributes = this.readFile((String) attributes.get(filePathField));
			if (turFileAttributes != null) {
				if (fileSizeField != null && turFileAttributes.getFile() != null) {
					attributes.put(fileSizeField, turFileAttributes.getFile().length());
					if (logger.isDebugEnabled()) {
						logger.debug("File: {}", turFileAttributes.getFile().getAbsolutePath());
						logger.debug("File size: {}",
								FileUtils.byteCountToDisplaySize(turFileAttributes.getFile().length()));
						logger.debug("File - Content size: "
								+ FileUtils.byteCountToDisplaySize(turFileAttributes.getContent().getBytes().length));
					}
				} else {
					logger.debug("File without size: {}", filePathField);
				}

				if (fileContentField != null) {
					long maxContentByteSize = maxContentMegaByteSize * MEGA_BYTE;

					if (turFileAttributes.getContent().getBytes().length <= maxContentByteSize) {
						attributes.put(fileContentField, turFileAttributes.getContent());
					} else {
						attributes.put(fileContentField,
								turFileAttributes.getContent().substring(0, Math.toIntExact(maxContentByteSize)));
						if (logger.isDebugEnabled()) {
							logger.debug("File size greater than {}, truncating content ...:",
									FileUtils.byteCountToDisplaySize(maxContentByteSize));
						}
					}
				} else {
					logger.debug("File without content: {}", filePathField);
				}
			}
		}
	}

	private void addMultiValuedAttributes(Map<String, Object> attributes) {
		attributes.entrySet().forEach(attribute -> {
			String attributeName = attribute.getKey();
			String attributeValue = String.valueOf(attribute.getValue());
			addOnlyMultiValuedAttributes(attributes, attributeName, attributeValue);
		});
	}

	private void addOnlyMultiValuedAttributes(Map<String, Object> attributes, String attributeName,
			String attributeValue) {
		for (String strMvField : mvField.toLowerCase().split(",")) {
			if (attributeName.equalsIgnoreCase(strMvField.toLowerCase()) && attributeValue != null) {
				List<String> multiValueList = Arrays.asList(attributeValue.split(mvSeparator));
				attributes.put(attributeName, multiValueList);
			}
		}
	}

	private void sendServer(TurSNServer turSNServer, TurChunkingJob turChunkingJob) {
		System.out.print(String.format("Importing %s to %s items\n", turChunkingJob.getFirstItemPosition(),
				turChunkingJob.getTotal()));
		turSNServer.importItems(turChunkingJob.getTurSNJobItems(), showOutput);
	}

	private static String cleanTextContent(String text) {
		text = text.replaceAll("[\r\n\t]", " ");
		text = text.replaceAll("[^\\p{L}&&[^0-9A-Za-z]&&[^\\p{javaSpaceChar}]&&[^\\p{Punct}]]", "").replaceAll("_{2,}",
				"");
		// Remove 2 or more spaces
		text = text.trim().replaceAll(" +", " ");
		return text.trim();
	}
}
