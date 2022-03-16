/*
 * Copyright (C) 2017-2020 the original author or authors. 
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
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.tool.file.TurFileAttributes;
import com.viglet.turing.tool.impl.TurJDBCCustomImpl;
import com.viglet.turing.tool.jdbc.format.TurFormatValue;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
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
public class JDBCImportTool {
	static final Logger logger = LogManager.getLogger(JDBCImportTool.class.getName());

	private static final long MEGA_BYTE = 1024L * 1024L;

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

		JDBCImportTool main = new JDBCImportTool();
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

	public void select() {

		if (this.deindexBeforeImporting) {
			this.indexDeleteByType();
		}

		TurJDBCCustomImpl turJDBCCustomImpl = null;
		if (customClassName != null) {
			try {
				turJDBCCustomImpl = (TurJDBCCustomImpl) Class.forName(customClassName).getDeclaredConstructor()
						.newInstance();
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}

		try {
			// Register JDBC driver
			Class.forName(driver);

			// Open a connection
			logger.info("Execute a query...");
			try (Connection conn = DriverManager.getConnection(connect, dbUsername, dbPassword);
					Statement stmt = conn.createStatement();
					ResultSet rs = stmt.executeQuery(query);) {

				// Extract data from result set
				int chunkCurrent = 0;
				int chunkTotal = 0;
				TurSNJobItems turSNJobItems = new TurSNJobItems();

				while (rs.next()) {
					Map<String, Object> attributes = new HashMap<>();
					attributes.put("type", type);
					addDBFieldsAsAttributes(rs, attributes);
					addFileAttributes(attributes);
					attributes = modifyAttributesByCustomClass(turJDBCCustomImpl, conn, attributes);
					addMultiValueAttributes(attributes);

					turSNJobItems.add(createTurSNJobItem(attributes));

					chunkTotal++;
					chunkCurrent++;
					if (chunkCurrent == chunk) {
						this.sendServer(turSNJobItems, chunkTotal);
						turSNJobItems = new TurSNJobItems();
						chunkCurrent = 0;
					}
				}
				if (chunkCurrent > 0) {
					this.sendServer(turSNJobItems, chunkTotal);
					turSNJobItems = new TurSNJobItems();
					chunkCurrent = 0;
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
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

	private TurSNJobItem createTurSNJobItem(Map<String, Object> attributes) {
		TurSNJobItem turSNJobItem = new TurSNJobItem();
		turSNJobItem.setTurSNJobAction(TurSNJobAction.CREATE);
		turSNJobItem.setAttributes(attributes);
		turSNJobItem.setLocale(locale);
		return turSNJobItem;
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
				logger.info("File: " + turFileAttributes.getFile().getAbsolutePath());
				if (fileSizeField != null && turFileAttributes.getFile() != null) {
					attributes.put(fileSizeField, turFileAttributes.getFile().length());

					logger.info("File size: " + FileUtils.byteCountToDisplaySize(turFileAttributes.getFile().length()));
					logger.info("File - Content size: "
							+ FileUtils.byteCountToDisplaySize(turFileAttributes.getContent().getBytes().length));
				} else {
					logger.info("File without size: " + filePathField);
				}

				if (fileContentField != null) {
					long maxContentByteSize = maxContentMegaByteSize * MEGA_BYTE;

					if (turFileAttributes.getContent().getBytes().length <= maxContentByteSize) {
						attributes.put(fileContentField, turFileAttributes.getContent());
					} else {
						attributes.put(fileContentField,
								turFileAttributes.getContent().substring(0, Math.toIntExact(maxContentByteSize)));
						logger.info("File size greater than {}, truncating content ...:",
								FileUtils.byteCountToDisplaySize(maxContentByteSize));
					}
				} else {
					logger.info("File without content: {}", filePathField);
				}
			}
		}
	}

	private void addMultiValueAttributes(Map<String, Object> attributes) {
		String[] strMvFields = mvField.toLowerCase().split(",");
		for (Entry<String, Object> atribute : attributes.entrySet()) {
			String attributeName = atribute.getKey();
			String attributeValue = String.valueOf(atribute.getValue());
			for (String strMvField : strMvFields) {
				if (attributeName.equalsIgnoreCase(strMvField.toLowerCase()) && attributeValue != null) {
					List<String> multiValueList = Arrays.asList(attributeValue.split(mvSeparator));
					attributes.put(attributeName, multiValueList);
				}
			}
		}
	}

	public void sendServer(TurSNJobItems turSNJobItems, int chunkTotal) {
		String jsonResult;
		try {
			jsonResult = new ObjectMapper().writeValueAsString(turSNJobItems);

			int initial = 1;
			if (chunkTotal > chunk) {
				initial = chunkTotal - chunk;
			}

			Charset utf8Charset = StandardCharsets.UTF_8;
			Charset customCharset = Charset.forName(encoding);

			ByteBuffer inputBuffer = ByteBuffer.wrap(jsonResult.getBytes());

			// decode UTF-8
			CharBuffer data = utf8Charset.decode(inputBuffer);

			// encode
			ByteBuffer outputBuffer = customCharset.encode(data);

			byte[] outputData = new String(outputBuffer.array()).getBytes(StandardCharsets.UTF_8);
			String jsonUTF8 = new String(outputData);

			System.out.print("Importing " + initial + " to " + chunkTotal + " items\n");
			try (CloseableHttpClient client = HttpClients.createDefault()) {
				HttpPost httpPost = new HttpPost(String.format("%s/api/sn/%s/import", turingServer, site));
				if (showOutput) {
					System.out.println(jsonUTF8);
				}
				StringEntity entity = new StringEntity(jsonUTF8, StandardCharsets.UTF_8);
				httpPost.setEntity(entity);
				httpPost.setHeader("Accept", MediaType.JSON_UTF_8.toString());
				httpPost.setHeader("Content-type", MediaType.JSON_UTF_8.toString());
				httpPost.setHeader("Accept-Encoding", StandardCharsets.UTF_8.name());

				basicAuth(httpPost);

				client.execute(httpPost);
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		} catch (JsonProcessingException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private void basicAuth(HttpPost httpPost) {
		if (turUsername != null) {
			String auth = String.format("%s:%s", turUsername, turPassword);
			String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
			String authHeader = "Basic " + encodedAuth;
			httpPost.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
		}
	}

	private static String cleanTextContent(String text) {
		text = text.replaceAll("[\r\n\t]", " ");
		text = text.replaceAll("[^\\p{L}&&[^0-9A-Za-z]&&[^\\p{javaSpaceChar}]&&[^\\p{Punct}]]", "").replaceAll("_{2,}",
				"");
		// Remove 2 or more spaces
		text = text.trim().replaceAll(" +", " ");
		return text.trim();
	}

	public boolean indexDeleteByType() {
		boolean success = false;
		try (CloseableHttpClient client = HttpClients.createDefault()) {
			HttpGet httpGet = new HttpGet(String.format("%s/api/otsn/broker?action=delete&index=%s&type=%s&config=none",
					this.getTuringServer(), this.getSite(), this.getType()));
			try (CloseableHttpResponse response = client.execute(httpGet)) {
				if (logger.isDebugEnabled()) {
					logger.debug("Viglet Turing Delete Request URI: {}", httpGet.getURI());
					logger.debug("Viglet Turing indexer response HTTP result is: {}",
							EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
				}
				success = true;
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
		return success;
	}
}
