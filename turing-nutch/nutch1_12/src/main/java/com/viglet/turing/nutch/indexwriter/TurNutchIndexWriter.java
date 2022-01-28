package com.viglet.turing.nutch.indexwriter;

import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.nutch.commons.TurNutchCommons;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.JobConf;
import org.apache.nutch.indexer.IndexWriter;
import org.apache.nutch.indexer.IndexerMapReduce;
import org.apache.nutch.indexer.NutchDocument;
import org.apache.nutch.indexer.NutchField;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import static java.net.URLDecoder.decode;

public class TurNutchIndexWriter implements IndexWriter {

	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static final String TIMESTAMP_PROPERTY = "turing.timestamp.field";
	private static final String FIELD_PROPERTY = "turing.field.";
	private final TurSNJobItems turSNJobItems = new TurSNJobItems();
	private ModifiableSolrParams params;
	private TurMappingReader turMapping;
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
			key = decode(key, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			logger.warn("Could not decode: " + key + ", it probably wasn't encoded in the first place..");
		}

		// escape solr hash separator
		key = key.replaceAll("!", "\\!");

		if (delete) {
			Map<String, Object> attributes = new HashMap<String, Object>();
			attributes.put(TurNutchCommons.ID_FIELD, key);
			turSNJobItem.setAttributes(attributes);
			turSNJobItems.add(turSNJobItem);
		}
		String snSite = turMapping.getSNSite(turSNJobItem.getAttributes().get("id").toString());

		if (snSite != null) {
			site = snSite;
		}

		TurNutchCommons.push(turSNJobItems, auth, totalAdds, username, password, url, site);

	}

	@Override
	public void update(NutchDocument doc) throws IOException {
		write(doc);
	}

	@Override
	public void write(NutchDocument doc) throws IOException {
		final TurSNJobItem turSNJobItem = new TurSNJobItem();
		turSNJobItem.setLocale(this.config.get(TurNutchConstants.LOCALE_PROPERTY, TurNutchCommons.LOCALE_DEFAULT_VALUE));
		turSNJobItem.setTurSNJobAction(TurSNJobAction.CREATE);
		Map<String, Object> attributes = new HashMap<>();
		Map<String, String> turCustomFields = this.config.getValByRegex("^" + FIELD_PROPERTY + "*");
		String localeField = this.config.get(TurNutchConstants.LOCALE_FIELD_PROPERTY);
		for (final Entry<String, NutchField> fieldMap : doc) {
			for (final Object originalValue : fieldMap.getValue().getValues()) {
				// normalize the string representation for a Date
				Object normalizedValue = originalValue;

				if (originalValue instanceof Date) {
					normalizedValue = DateTimeFormatter.ISO_INSTANT.format(((Date) originalValue).toInstant());
				}

				if (fieldMap.getKey().equals(TurNutchCommons.CONTENT_FIELD)
						|| fieldMap.getKey().equals(TurNutchCommons.TITLE_FIELD)) {
					normalizedValue = TurNutchCommons.stripNonCharCodepoints((String) originalValue);
				}
				if (localeField != null && fieldMap.getKey().equals(TurNutchCommons.META_TAG_VALUE + localeField)) {
					turSNJobItem.setLocale(TurNutchCommons.stripNonCharCodepoints((String) originalValue));
				} else if (fieldMap.getKey().equals(TurNutchCommons.TIMESTAMP_FIELD)) {
					attributes.put(this.config.get(TIMESTAMP_PROPERTY, TurNutchCommons.TIMESTAMP_FIELD),
							normalizedValue);
				} else {
					attributes.put(turMapping.mapKey(fieldMap.getKey()), normalizedValue);
					String sCopy = turMapping.mapCopyKey(fieldMap.getKey());
					if (sCopy != fieldMap.getKey())
						attributes.put(sCopy, normalizedValue);
				}
			}
		}

		if (!weightField.isEmpty()) {
			attributes.put(weightField, doc.getWeight());
		}

		attributes.put(TurNutchCommons.TYPE_FIELD,
				this.config.get(FIELD_PROPERTY + TurNutchCommons.TYPE_FIELD, TurNutchCommons.TYPE_DEFAULT_VALUE));
		attributes.put(TurNutchCommons.CONNECTOR_FIELD, this.config
				.get(FIELD_PROPERTY + TurNutchCommons.CONNECTOR_FIELD, TurNutchCommons.CONNECTOR_DEFAULT_VALUE));

		turCustomFields.entrySet().forEach(turCustomField -> {
			String[] keyFullName = turCustomField.getKey().split("\\.");
			String key = keyFullName[keyFullName.length - 1];
			if (!key.equals(TurNutchCommons.TYPE_FIELD) && (!key.equals(TurNutchCommons.CONNECTOR_FIELD))) {
				attributes.put(key, turCustomField.getValue());
			}
		});
		turSNJobItem.setAttributes(attributes);
		turSNJobItems.add(turSNJobItem);
		totalAdds++;
		String snSite = turMapping.getSNSite(turSNJobItem.getAttributes().get("id").toString());

		if (snSite != null) {
			site = snSite;
		}

		TurNutchCommons.push(turSNJobItems, auth, totalAdds, username, password, url, site);
	}

	@Override
	public void close() throws IOException {
		// Nothing
	}

	@Override
	public void commit() throws IOException {
		TurNutchCommons.push(turSNJobItems, auth, totalAdds, username, password, url, site);
	}

	@Override
	public Configuration getConf() {
		return config;
	}

	@Override
	public void setConf(Configuration conf) {
		config = conf;
	}

	@Override
	public void open(JobConf job, String name) {
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
		this.username = this.config.get(TurNutchConstants.USERNAME, TurNutchCommons.USERNAME_DEFAULT_VALUE);
		this.password = this.config.get(TurNutchConstants.PASSWORD, TurNutchCommons.PASSWORD_DEFAULT_VALUE);

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
		String[] fullUrl = this.config.get(TurNutchConstants.SOLR_SERVER_URL).split(TurNutchCommons.SLASH);
		this.site = fullUrl[fullUrl.length - 1];
		String[] partialUrl = Arrays.copyOf(fullUrl, fullUrl.length - 1);
		this.url = String.join(TurNutchCommons.SLASH, partialUrl);
	}

	private void useTuringConfig() {
		this.url = this.config.get(TurNutchConstants.SERVER_URL, TurNutchCommons.TURING_SERVER_DEFAULT_VALUE);
		this.site = this.config.get(TurNutchConstants.SITE, TurNutchCommons.SITE_DEFAULT_VALUE);
	}

	private void init(JobConf job) {
		turMapping = TurMappingReader.getInstance(job);
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
		sb.append(System.lineSeparator()).append(TurNutchCommons.TAB)
				.append("Indexing to Viglet Turing Semantic Navigation");
		return sb.toString();
	}

	void describeLine(StringBuffer sb, String variable, String description, String value) {
		sb.append(System.lineSeparator()).append(variable).append(TurNutchCommons.TAB).append(description)
				.append(TurNutchCommons.TAB).append(value);

	}

}
