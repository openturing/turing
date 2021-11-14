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

package com.viglet.turing.plugins.nlp.polyglot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

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
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.google.common.io.CharStreams;
import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.plugins.nlp.TurNLPPlugin;
import com.viglet.turing.solr.TurSolrField;
import com.viglet.turing.utils.TurUtils;
import java.util.*;

@Component
public class TurPolyglotConnector implements TurNLPPlugin {
	private static final Logger logger = LogManager.getLogger(TurPolyglotConnector.class);

	@Autowired
	private TurSolrField turSolrField;

	public Map<String, List<String>> processAttributesToEntityMap(TurNLP turNLP) {
		return this.request(turNLP);
	}

	public Map<String, List<String>> request(TurNLP turNLP) {
		Map<String, List<String>> entityList = new HashMap<>();

		if (turNLP.getAttributeMapToBeProcessed() != null) {
			for (Object attrValue : turNLP.getAttributeMapToBeProcessed().values()) {

				for (String atributeValue : createSentences(attrValue)) {
					processSentence(turNLP, entityList, atributeValue);
				}
			}

		}
		return this.getAttributes(turNLP, entityList);

	}

	private void processSentence(TurNLP turNLP, Map<String, List<String>> entityList, String atributeValue) {
		HttpPost httpPost = prepareHttpPost(turNLP, atributeValue);
		if (httpPost != null) {
			try (CloseableHttpClient httpclient = HttpClients.createDefault();
					CloseableHttpResponse response = httpclient.execute(httpPost)) {
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					try (BufferedReader rd = new BufferedReader(
							new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8))) {
						String jsonResponse = CharStreams.toString(rd);
						if (TurUtils.isJSONValid(jsonResponse)) {
							if (logger.isDebugEnabled()) {
								logger.debug("Polyglot JSONResponse: {}", jsonResponse);
							}
							this.getEntitiesPolyglot(new JSONArray(jsonResponse), entityList);
						}
					}
				}
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	private HttpPost prepareHttpPost(TurNLP turNLP, String atributeValue) {
		URL serverURL = getServerURL(turNLP);
		if (serverURL != null) {
			JSONObject jsonBody = prepareJSONResponse(turNLP, atributeValue);

			HttpPost httpPost = new HttpPost(serverURL.toString());

			httpPost.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
			httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
			httpPost.setHeader(HttpHeaders.ACCEPT_ENCODING, StandardCharsets.UTF_8.name());
			StringEntity stringEntity = new StringEntity(jsonBody.toString(), StandardCharsets.UTF_8);
			httpPost.setEntity(stringEntity);
			return httpPost;
		} else {
			return null;
		}
	}

	private JSONObject prepareJSONResponse(TurNLP turNLP, String atributeValue) {
		Charset utf8Charset = StandardCharsets.UTF_8;
		Charset customCharset = StandardCharsets.UTF_8;
		if (logger.isDebugEnabled()) {
			logger.debug("Polyglot Text: {}", atributeValue);
		}
		JSONObject jsonBody = new JSONObject();
		jsonBody.put("text", atributeValue);

		jsonBody.put("model", turNLP.getTurNLPInstance().getLanguage());

		ByteBuffer inputBuffer = ByteBuffer.wrap(jsonBody.toString().getBytes());

		// decode UTF-8
		CharBuffer data = utf8Charset.decode(inputBuffer);

		// encode
		ByteBuffer outputBuffer = customCharset.encode(data);

		byte[] outputData = new String(outputBuffer.array()).getBytes(StandardCharsets.UTF_8);
		String jsonUTF8 = new String(outputData);

		logger.debug("Polyglot JSONBody: {}", jsonUTF8);
		return jsonBody;
	}

	private URL getServerURL(TurNLP turNLP) {
		try {
			return new URL("http", turNLP.getTurNLPInstance().getHost(), turNLP.getTurNLPInstance().getPort(), "/ent");
		} catch (MalformedURLException e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}


	private void handleEntityPolyglot(String entityType, String entity, Map<String, List<String>> entityList) {
		if (entityList.containsKey(entityType)) {
			if (!entityList.get(entityType).contains(entity) && entity.trim().length() > 1) {
				entityList.get(entityType).add(entity.trim());
			}
		} else {
			List<String> valueList = new ArrayList<>();
			valueList.add(entity.trim());
			entityList.put(entityType, valueList);
		}

	}
	
	private String[] createSentences(Object attrValue) {
		return cleanFullText(attrValue).split("\\.");
	}

	private String cleanFullText(Object attrValue) {
		return turSolrField.convertFieldToString(attrValue).replaceAll("[\\n:;]", ". ")
				.replaceAll("\\h|\\r|\\n|\"|\'|R\\$", " ").replaceAll("\\.+", ". ").replaceAll(" +", " ").trim();
	}

	public Map<String, List<String>> getAttributes(TurNLP turNLP, Map<String, List<String>> entityList) {
		Map<String, List<String>> entityAttributes = new HashMap<>();

		for (TurNLPInstanceEntity turNLPInstanceEntity : turNLP.getNlpInstanceEntities()) {
			entityAttributes.put(turNLPInstanceEntity.getTurNLPEntity().getInternalName(),
					this.getEntity(turNLPInstanceEntity.getTurNLPEntity().getInternalName(), entityList));
		}
		return entityAttributes;
	}

	public List<String> getEntity(String entity, Map<String, List<String>> entityList) {
		if (logger.isDebugEnabled()) {
			logger.debug("getEntity: {}", entity);
		}
		return entityList.get(entity);
	}
	
	public void getEntitiesPolyglot(JSONArray json, Map<String, List<String>> entityList) {

		for (int i = 0; i < json.length(); i++) {
			boolean add = true;
			JSONObject token = (JSONObject) json.get(i);

			String term = token.getString("token");
			String label = token.getString("label");

			if (label.equals("NAME"))
				add = false;

			if (add)
				this.handleEntityPolyglot(label, term, entityList);
		}

	}



}
