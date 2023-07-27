package com.viglet.turing.nutch.indexwriter;

import java.lang.invoke.MethodHandles;
import java.net.URLDecoder;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.indexer.IndexWriter;
import org.apache.nutch.indexer.IndexWriterParams;
import org.apache.nutch.indexer.IndexerMapReduce;
import org.apache.nutch.indexer.NutchDocument;
import org.apache.nutch.indexer.NutchField;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.nutch.commons.TurNutchCommons;

public class TurNutchIndexWriter implements IndexWriter {

	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private final TurSNJobItems turSNJobItems = new TurSNJobItems();
	private ModifiableSolrParams params;
	private Configuration config;

	private String url;
	private String site;
	private boolean auth;

	private int batchSize;
	private int totalAdds = 0;
	private boolean delete = false;
	private String weightField;

	private String username;
	private String password;

	@Override
	public void open(Configuration conf, String name) {
		// Implementation not required
	}

	@Override
	public void open(IndexWriterParams parameters) {
		if (this.config.get("solr.server.url") != null) {
			String[] fullUrl = this.config.get("solr.server.url").split("/");
			this.site = fullUrl[fullUrl.length - 1];
			String[] partialUrl = Arrays.copyOf(fullUrl, fullUrl.length - 1);
			this.url = String.join("/", partialUrl);
		} else {
			this.url = this.config.get("turing.".concat(TurNutchConstants.SERVER_URL)) != null
					? this.config.get("turing.".concat(TurNutchConstants.SERVER_URL))
					: parameters.get(TurNutchConstants.SERVER_URL);

			this.site = this.config.get("turing.".concat(TurNutchConstants.SITE)) != null
					? this.config.get("turing.".concat(TurNutchConstants.SITE))
					: parameters.get(TurNutchConstants.SITE);
		}
		if (url == null) {
			String message = "Missing Turing URL.\n" + describe();
			logger.error(message);
			throw new RuntimeException(message);
		}
		this.auth = this.config.get("turing.".concat(TurNutchConstants.USE_AUTH)) != null
				? this.config.getBoolean("turing.".concat(TurNutchConstants.USE_AUTH), false)
				: parameters.getBoolean(TurNutchConstants.USE_AUTH, false);

		this.username = this.config.get("turing.".concat(TurNutchConstants.USERNAME)) != null
				? this.config.get("turing.".concat(TurNutchConstants.USERNAME))
				: parameters.get(TurNutchConstants.USERNAME);
		this.password = this.config.get("turing.".concat(TurNutchConstants.PASSWORD)) != null
				? this.config.get("turing.".concat(TurNutchConstants.PASSWORD))
				: parameters.get(TurNutchConstants.PASSWORD);

		init(parameters);
	}

	private void init(IndexWriterParams properties) {
		delete = config.getBoolean(IndexerMapReduce.INDEXER_DELETE, false);
		batchSize = properties.getInt(TurNutchConstants.COMMIT_SIZE, 1000);
		weightField = properties.get(TurNutchConstants.WEIGHT_FIELD, "");
		// parse optional parameters
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

		// escape Solr hash separator
		key = key.replaceAll("!", "\\!");

		if (delete) {
			Map<String, Object> attributes = new HashMap<String, Object>();
			attributes.put("id", key);
			turSNJobItem.setAttributes(attributes);
			turSNJobItems.add(turSNJobItem);
		}

		if (turSNJobItems.getTuringDocuments().size() >= batchSize) {
			TurNutchCommons.push(turSNJobItems, auth, totalAdds, username, password, url, site);
		}

	}

	@Override
	public void update(NutchDocument doc) throws IOException {
		write(doc);
	}

	@Override
	public void write(NutchDocument doc) throws IOException {
		System.out.println(doc.toString());
		final TurSNJobItem turSNJobItem = new TurSNJobItem();
		turSNJobItem.setLocale(this.config.get("turing.".concat(TurNutchConstants.LOCALE_PROPERTY), TurNutchCommons.LOCALE_DEFAULT_VALUE));
		turSNJobItem.setTurSNJobAction(TurSNJobAction.CREATE);
		Map<String, Object> attributes = new HashMap<String, Object>();
		for (final Entry<String, NutchField> e : doc) {

			for (final Object val : e.getValue().getValues()) {
				// Normalize the string representation for a Date
				Object val2 = val;

				if (val instanceof Date) {
					val2 = DateTimeFormatter.ISO_INSTANT.format(((Date) val).toInstant());
				}

				if (e.getKey().equals(TurNutchCommons.CONTENT_FIELD) || e.getKey().equals(TurNutchCommons.TITLE_FIELD)) {
					val2 = TurNutchCommons.stripNonCharCodepoints((String) val);
				}
				if (e.getKey().equals(TurNutchCommons.CONTENT_FIELD)) {
					attributes.put(TurNutchCommons.TEXT_FIELD, val2);
				} else {
					attributes.put(e.getKey(), val2);
				}
			}
		}

		if (!weightField.isEmpty()) {
			attributes.put(weightField, doc.getWeight());
		}

		attributes.put(TurNutchCommons.TYPE_FIELD, TurNutchCommons.TYPE_DEFAULT_VALUE);
		attributes.put(TurNutchCommons.CONNECTOR_FIELD, TurNutchCommons.CONNECTOR_DEFAULT_VALUE);
		turSNJobItem.setAttributes(attributes);
		turSNJobItems.add(turSNJobItem);
		totalAdds++;

		if (turSNJobItems.getTuringDocuments().size() >= batchSize) {
			TurNutchCommons.push(turSNJobItems, auth, totalAdds, username, password, url, site);
		}
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
	public Map<String, Entry<String, Object>> describe() {

		Map<String, Entry<String, Object>> properties = new LinkedHashMap<>();
		properties.put(TurNutchConstants.SERVER_URL,
				new AbstractMap.SimpleEntry<>("Defines the fully qualified URL of Turing.", this.url));
		properties.put(TurNutchConstants.SITE,
				new AbstractMap.SimpleEntry<>("Defines the Turing Semantic Navigation Site.", this.site));
		properties.put(TurNutchConstants.COMMIT_SIZE, new AbstractMap.SimpleEntry<>(
				"Defines the number of documents to send to Turing in a single update batch. "
						+ "Decrease when handling very large documents to prevent Nutch from running out of memory.\n"
						+ "Note: It does not explicitly trigger a server side commit.",
				this.batchSize));
		properties.put(TurNutchConstants.WEIGHT_FIELD, new AbstractMap.SimpleEntry<>(
				"Field's name where the weight of the documents will be written. If it is empty no field will be used.",
				this.weightField));
		properties.put(TurNutchConstants.USE_AUTH, new AbstractMap.SimpleEntry<>(
				"Whether to enable HTTP basic authentication for communicating with Turing. Use the username and password properties to configure your credentials.",
				this.auth));
		properties.put(TurNutchConstants.USERNAME,
				new AbstractMap.SimpleEntry<>("The username of Turing server.", this.username));
		properties.put(TurNutchConstants.PASSWORD,
				new AbstractMap.SimpleEntry<>("The password of Turing server.", this.password));

		return properties;
	}
}
