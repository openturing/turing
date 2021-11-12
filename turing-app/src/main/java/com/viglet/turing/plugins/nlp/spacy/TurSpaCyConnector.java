/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

package com.viglet.turing.plugins.nlp.spacy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;
import com.viglet.turing.plugins.nlp.TurNLPImpl;
import com.viglet.turing.solr.TurSolrField;
import com.viglet.turing.utils.TurUtils;

import java.util.*;

@Component
public class TurSpaCyConnector implements TurNLPImpl {
	static final Logger logger = LogManager.getLogger(TurSpaCyConnector.class);

	@Autowired
	private TurNLPEntityRepository turNLPEntityRepository;
	@Autowired
	private TurSolrField turSolrField;
	@Autowired
	private TurUtils turUtils;
	private List<TurNLPEntity> nlpEntities = null;
	private Map<String, List<Object>> entityList = new HashMap<>();
	private TurNLPInstance turNLPInstance = null;

	public void startup(TurNLPInstance turNLPInstance) {
		this.turNLPInstance = turNLPInstance;

		nlpEntities = turNLPEntityRepository.findByEnabled(1);
	}

	public Map<String, Object> retrieve(Map<String, Object> attributes) {
		return this.request(this.turNLPInstance, attributes);
	}

	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}

	public Map<String, Object> request(TurNLPInstance turNLPInstance, Map<String, Object> attributes){
		try (CloseableHttpClient httpclient = HttpClients.createDefault()){
		URL serverURL = new URL("http", turNLPInstance.getHost(), turNLPInstance.getPort(), "/ent");
		if (logger.isDebugEnabled()) {
			logger.debug("URL: {}", serverURL);
		}

		
		
			HttpPost httpPost = new HttpPost(serverURL.toString());
			Charset utf8Charset = StandardCharsets.UTF_8;
			Charset customCharset = StandardCharsets.UTF_8;

			if (attributes != null) {
				for (Object attrValue : attributes.values()) {
					JSONObject jsonBody = new JSONObject();
					String atributeValueFullText = turUtils.removeUrl(turSolrField.convertFieldToString(attrValue))
							.replaceAll("[\\n:;]", ". ").replaceAll("\\h|\\r|\\n|\"|\'|R\\$", " ")
							.replaceAll("\\.+", ". ")
							.replaceAll(" +", " ").trim();

					for (String atributeValue : atributeValueFullText.split("\\.")) {
						if (logger.isDebugEnabled()) {
							logger.debug("SpaCy Text: {}", atributeValue);
						}
						jsonBody.put("text", atributeValue);

						if (turNLPInstance.getLanguage().equals(TurLocaleRepository.PT_BR)) {
							jsonBody.put("model", "pt_core_news_sm");
						} else {
							jsonBody.put("model", turNLPInstance.getLanguage());
						}

						ByteBuffer inputBuffer = ByteBuffer.wrap(jsonBody.toString().getBytes());

						// decode UTF-8
						CharBuffer data = utf8Charset.decode(inputBuffer);

						// encode
						ByteBuffer outputBuffer = customCharset.encode(data);

						byte[] outputData = new String(outputBuffer.array()).getBytes(StandardCharsets.UTF_8);
						String jsonUTF8 = new String(outputData);

						if (logger.isDebugEnabled()) {
							logger.debug("SpaCy JSONBody: {}" , jsonUTF8);
						}
						httpPost.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
						httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
						httpPost.setHeader(HttpHeaders.ACCEPT_ENCODING, StandardCharsets.UTF_8.name());
						StringEntity stringEntity = new StringEntity(jsonBody.toString(),StandardCharsets.UTF_8);
						httpPost.setEntity(stringEntity);

						try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
							HttpEntity entity = response.getEntity();

							if (entity != null) {
								try (InputStream instream = entity.getContent()) {
									BufferedReader rd = new BufferedReader(
											new InputStreamReader(instream, StandardCharsets.UTF_8));
									String jsonResponse = readAll(rd);
									if (TurUtils.isJSONValid(jsonResponse)) {
										if (logger.isDebugEnabled()) {
											logger.debug("SpaCy JSONResponse: {}" , jsonResponse);
										}
										this.getEntities(atributeValue, new JSONArray(jsonResponse));
									}
								}
							}
						}
					}
				}
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		return this.getAttributes();

	}

	public Map<String, Object> getAttributes() throws JSONException {
		Map<String, Object> entityAttributes = new HashMap<>();

		for (TurNLPEntity nlpEntity : nlpEntities) {
			entityAttributes.put(nlpEntity.getInternalName(), this.getEntity(nlpEntity.getInternalName()));
		}
		if (logger.isDebugEnabled()) {
			logger.debug("SpaCy getAttributes: {}", entityAttributes);
		}
		return entityAttributes;
	}

	public void getEntities(String text, JSONArray json) throws JSONException {

		for (int i = 0; i < json.length(); i++) {
			boolean add = true;
			JSONObject token = (JSONObject) json.get(i);

			int tokenStart = token.getInt("start");
			int tokenEnd = token.getInt("end");
			String label = token.getString("label");

			String term = text.substring(tokenStart, tokenEnd);

			if (label.equals("ORG")) {
				label = "ON";
				if (!Character.isUpperCase(term.charAt(0)))
					add = false;
			}
			if (label.equals("PER"))
				label = "PN";

			if (logger.isDebugEnabled()) {
				logger.debug("SpaCy Term (NER): {} ({})", term, label);
			}

			if (add)
				this.handleEntity(label, term);
		}

	}

	public List<Object> getEntity(String entity) {
		if (logger.isDebugEnabled()) {
			logger.debug("getEntity: {}", entity);
		}
		return entityList.get(entity);
	}

	private void handleEntity(String entityType, String entity) {
		if (entityList.containsKey(entityType)) {
			if (!entityList.get(entityType).contains(entity) && entity.trim().length() > 1) {
				entityList.get(entityType).add(entity.trim());
			}
		} else {
			List<Object> valueList = new ArrayList<>();
			valueList.add(entity.trim());
			entityList.put(entityType, valueList);
		}

	}
}
