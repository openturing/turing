package com.viglet.turing.nutch.indexwriter;

import java.lang.invoke.MethodHandles;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.JobConf;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.nutch.indexer.IndexWriter;
import org.apache.nutch.indexer.IndexerMapReduce;
import org.apache.nutch.indexer.NutchDocument;
import org.apache.nutch.indexer.NutchField;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.nutch.commons.TurNutchCommons;

public class TurNutchIndexWriter implements IndexWriter {

	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static final String CONTENT_FIELD = "content";
	private static final String TITLE_FIELD = "title";
	private static final String TEXT_FIELD = "text";
	private static final String TIMESTAMP_FIELD = "tstamp";
	private static final String TYPE_FIELD = "type";
	private static final String CONNECTOR_FIELD = "source_app";
	private static final String TYPE_DEFAULT_VALUE = "Page";
	private static final String CONNECTOR_DEFAULT_VALUE = "Nutch";
	private static final String USERNAME_DEFAULT_VALUE = "admin";
	private static final String PASSWORD_DEFAULT_VALUE = "admin";
	private static final String SITE_DEFAULT_VALUE = "Sample";
	private static final String TURING_SERVER_DEFAULT_VALUE = "http://localhost:2700";
	private static final String TIMESTAMP_PROPERTY = "turing.timestamp.field";
	private static final String FIELD_PROPERTY = "turing.field.";
	private static final char TAB = '\t';
	private static final String SLASH = "/";
	private final TurSNJobItems turSNJobItems = new TurSNJobItems();
	private CloseableHttpClient client = HttpClients.createDefault();
	private ModifiableSolrParams params;

	private Configuration config;

	private String url;
	private String site;
	private boolean auth;

	private int totalAdds = 0;
	private boolean delete = false;
	private String weightField;

	private String username;
	private String password;

	@Override
	public void delete(String key) throws IOException {
		final TurSNJobItem turSNJobItem = new TurSNJobItem();
		turSNJobItem.setTurSNJobAction(TurSNJobAction.DELETE);

		try {
			key = URLDecoder.decode(key, "UTF8");
		} catch (UnsupportedEncodingException e) {
			logger.error("Error decoding: " + key);
			throw new IOException("UnsupportedEncodingException for " + key);
		} catch (IllegalArgumentException e) {
			logger.warn("Could not decode: " + key + ", it probably wasn't encoded in the first place..");
		}

		// escape solr hash separator
		key = key.replaceAll("!", "\\!");

		if (delete) {
			Map<String, Object> attributes = new HashMap<String, Object>();
			attributes.put("id", key);
			turSNJobItem.setAttributes(attributes);
			turSNJobItems.add(turSNJobItem);
		}

		push();

	}

	@Override
	public void update(NutchDocument doc) throws IOException {
		write(doc);
	}

	@Override
	public void write(NutchDocument doc) throws IOException {
		final TurSNJobItem turSNJobItem = new TurSNJobItem();
		turSNJobItem.setTurSNJobAction(TurSNJobAction.CREATE);
		Map<String, Object> attributes = new HashMap<String, Object>();
		Map<String, String> turCustomFields = this.config.getValByRegex("^" + FIELD_PROPERTY + "*");

		for (final Entry<String, NutchField> fieldMap : doc) {
			for (final Object originalValue : fieldMap.getValue().getValues()) {
				// normalize the string representation for a Date
				Object normalizedValue = originalValue;

				if (originalValue instanceof Date) {
					normalizedValue = DateTimeFormatter.ISO_INSTANT.format(((Date) originalValue).toInstant());
				}

				if (fieldMap.getKey().equals(CONTENT_FIELD) || fieldMap.getKey().equals(TITLE_FIELD)) {
					normalizedValue = TurNutchCommons.stripNonCharCodepoints((String) originalValue);
				}
				if (fieldMap.getKey().equals(CONTENT_FIELD)) {
					attributes.put(TEXT_FIELD, normalizedValue);
				} else if (fieldMap.getKey().equals(TIMESTAMP_FIELD)) {
					attributes.put(this.config.get(TIMESTAMP_PROPERTY, TIMESTAMP_FIELD), normalizedValue);
				} else {
					attributes.put(fieldMap.getKey(), normalizedValue);
				}
			}
		}

		if (!weightField.isEmpty()) {
			attributes.put(weightField, doc.getWeight());
		}

		attributes.put(TYPE_FIELD, this.config.get(FIELD_PROPERTY + TYPE_FIELD, TYPE_DEFAULT_VALUE));
		attributes.put(CONNECTOR_FIELD, this.config.get(FIELD_PROPERTY + CONNECTOR_FIELD, CONNECTOR_DEFAULT_VALUE));

		turCustomFields.entrySet().forEach(turCustomField -> {
			String[] keyFullName = turCustomField.getKey().split("\\.");
			String key = keyFullName[keyFullName.length - 1];
			if (!key.equals(TYPE_FIELD) && (!key.equals(CONNECTOR_FIELD))) {
				attributes.put(key, turCustomField.getValue());
			}
		});
		turSNJobItem.setAttributes(attributes);
		turSNJobItems.add(turSNJobItem);
		totalAdds++;
		push();
	}

	@Override
	public void close() throws IOException {
		// Nothing
	}

	@Override
	public void commit() throws IOException {
		push();
	}

	private void push() throws IOException {
		if (turSNJobItems.getTuringDocuments().size() > 0) {
			int totalCreate = 0;
			int totalDelete = 0;

			for (TurSNJobItem turSNJobItem : turSNJobItems.getTuringDocuments()) {
				TurSNJobAction turSNJobAction = turSNJobItem.getTurSNJobAction();
				switch (turSNJobAction) {
				case CREATE:
					totalCreate++;
					break;
				case DELETE:
					totalDelete++;
					break;
				}
			}

			logger.info(String.format("Indexing %d/%d documents", totalCreate, totalAdds));
			logger.info(String.format("Deleting %d documents", totalDelete));

			boolean showOutput = false;

			ObjectMapper mapper = new ObjectMapper();
			String jsonResult = mapper.writeValueAsString(turSNJobItems);

			Charset utf8Charset = StandardCharsets.UTF_8;
			Charset customCharset = StandardCharsets.UTF_8;

			ByteBuffer inputBuffer = ByteBuffer.wrap(jsonResult.getBytes());

			// decode UTF-8
			CharBuffer data = utf8Charset.decode(inputBuffer);

			// encode
			ByteBuffer outputBuffer = customCharset.encode(data);

			byte[] outputData = new String(outputBuffer.array()).getBytes(StandardCharsets.UTF_8);
			String jsonUTF8 = new String(outputData);

			HttpPost httpPost = new HttpPost(String.format("%s/api/sn/%s/import", this.url, this.site));
			if (showOutput) {
				logger.info(jsonUTF8);
			}
			StringEntity entity = new StringEntity(new String(jsonUTF8), StandardCharsets.UTF_8);
			httpPost.setEntity(entity);
			httpPost.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
			httpPost.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
			httpPost.setHeader(HttpHeaders.ACCEPT_ENCODING, StandardCharsets.UTF_8.name());

			if (this.auth) {
				basicAuth(httpPost);
			}

			try (CloseableHttpResponse response = client.execute(httpPost)) {
				turSNJobItems.getTuringDocuments().clear();
			} catch (IOException e) {
				logger.error("Error", e);
			}
		}
	}

	@Override
	public Configuration getConf() {
		return config;
	}

	@Override
	public void setConf(Configuration conf) {
		config = conf;
	}

	private void basicAuth(HttpPost httpPost) {
		if (this.username != null) {
			String auth = String.format("%s:%s", this.username, this.password);
			String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
			String authHeader = "Basic " + encodedAuth;
			httpPost.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
		}
	}

	@Override
	public void open(JobConf job, String name) throws IOException {

		if (this.config.get(TurNutchConstants.FORCE_CONFIG) != null
				&& this.config.get(TurNutchConstants.FORCE_CONFIG).equals(String.valueOf(true))) {
			useTuringConfig();
		} else {
			if (this.config.get(TurNutchConstants.SOLR_SERVER_URL) != null) {
				useSolrConfig();
			} else {
				useTuringConfig();
			}
		}
		if (url == null) {
			String message = String.format("Missing Turing URL. %s %s", System.lineSeparator(), describe());
			logger.error(message);
			throw new RuntimeException(message);
		}

		this.auth = this.config.getBoolean(TurNutchConstants.USE_AUTH, false);
		this.username = this.config.get(TurNutchConstants.USERNAME, USERNAME_DEFAULT_VALUE);
		this.password = this.config.get(TurNutchConstants.PASSWORD, PASSWORD_DEFAULT_VALUE);

		init(job);

		logger.info(describeText());
	}

	private String describeText() {
		StringBuffer sb = new StringBuffer();
		describeLine(sb, TurNutchConstants.SERVER_URL, "Defines the fully qualified URL of Turing.", this.url);
		describeLine(sb, TurNutchConstants.SITE, "Defines the Turing Semantic Navigation Site.", this.site);
		describeLine(sb, TurNutchConstants.WEIGHT_FIELD,
				"Field's name where the weight of the documents will be written. If it is empty no field will be used.",
				this.weightField);
		describeLine(sb, TurNutchConstants.USE_AUTH,
				"Whether to enable HTTP basic authentication for communicating with Turing. Use the username and password properties to configure your credentials.",
				Boolean.toString(this.auth));
		describeLine(sb, TurNutchConstants.USERNAME, "The username of Turing server.", this.username);
		describeLine(sb, TurNutchConstants.PASSWORD, "The password of Turing server.", this.password);
		return sb.toString();
	}

	private void useSolrConfig() {
		String[] fullUrl = this.config.get(TurNutchConstants.SOLR_SERVER_URL).split(SLASH);
		this.site = fullUrl[fullUrl.length - 1];
		String[] partialUrl = Arrays.copyOf(fullUrl, fullUrl.length - 1);
		this.url = String.join(SLASH, partialUrl);
	}

	private void useTuringConfig() {
		this.url = this.config.get(TurNutchConstants.SERVER_URL, TURING_SERVER_DEFAULT_VALUE);
		this.site = this.config.get(TurNutchConstants.SITE, SITE_DEFAULT_VALUE);
	}

	private void init(JobConf job) {
		delete = config.getBoolean(IndexerMapReduce.INDEXER_DELETE, false);
		weightField = job.get(TurNutchConstants.WEIGHT_FIELD, StringUtils.EMPTY);
		// parse optional params
		params = new ModifiableSolrParams();
		String paramString = config.get(IndexerMapReduce.INDEXER_PARAMS);
		if (paramString != null) {
			String[] values = paramString.split("&");
			for (String v : values) {
				String[] kv = v.split("=");
				if (kv.length < 2) {
					continue;
				}
				params.add(kv[0], kv[1]);
			}
		}
	}

	@Override
	public String describe() {
		StringBuffer sb = new StringBuffer("TurNutchIndexWriter");
		sb.append(System.lineSeparator()).append(TAB).append("Indexing to Viglet Turing Semantic Navigation");
		return sb.toString();
	}

	void describeLine(StringBuffer sb, String variable, String description, String value) {
		sb.append(System.lineSeparator()).append(variable).append(TAB).append(description).append(TAB).append(value);

	}


}
