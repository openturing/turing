/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.plugins.nlp.polyglot;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.nlp.TurNLPEntityRequest;
import com.viglet.turing.nlp.TurNLPRequest;
import com.viglet.turing.plugins.nlp.TurNLPPlugin;
import com.viglet.turing.solr.TurSolrField;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TurPolyglotConnector implements TurNLPPlugin {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	public Map<String, List<String>> processAttributesToEntityMap(TurNLPRequest turNLPRequest) {
		return this.request(turNLPRequest);
	}

	public Map<String, List<String>> request(TurNLPRequest turNLPRequest) {
		Map<String, List<String>> entityList = new HashMap<>();

		if (turNLPRequest.getData() != null) {
			for (Object attrValue : turNLPRequest.getData().values()) {

				for (String atributeValue : createSentences(attrValue)) {
					processSentence(turNLPRequest, entityList, atributeValue);
				}
			}

		}
		return this.getAttributes(turNLPRequest, entityList);

	}

	private void processSentence(TurNLPRequest turNLPRequest, Map<String, List<String>> entityList, String atributeValue) {
		HttpPost httpPost = prepareHttpPost(turNLPRequest, atributeValue);
		if (httpPost != null) {
			try (CloseableHttpClient httpclient = HttpClients.createDefault();
					CloseableHttpResponse response = httpclient.execute(httpPost)) {
				HttpEntity entity = response.getEntity();
				if (entity != null) {
					String jsonResponse = new String(entity.getContent().readAllBytes(), StandardCharsets.UTF_8);
					if (TurCommonsUtils.isJSONValid(jsonResponse)) {
						if (logger.isDebugEnabled()) {
							logger.debug("Polyglot JSONResponse: {}", jsonResponse);
						}
						this.getEntitiesPolyglot(new JSONArray(jsonResponse), entityList);
					}
				}
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
	}

	private HttpPost prepareHttpPost(TurNLPRequest turNLPRequest, String atributeValue) {
		URL serverURL = getServerURL(turNLPRequest);
		if (serverURL != null) {
			JSONObject jsonBody = prepareJSONResponse(turNLPRequest, atributeValue);

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

	private JSONObject prepareJSONResponse(TurNLPRequest turNLPRequest, String atributeValue) {
		Charset utf8Charset = StandardCharsets.UTF_8;
		Charset customCharset = StandardCharsets.UTF_8;
		if (logger.isDebugEnabled()) {
			logger.debug("Polyglot Text: {}", atributeValue);
		}
		JSONObject jsonBody = new JSONObject();
		jsonBody.put("text", atributeValue);

		jsonBody.put("model", turNLPRequest.getTurNLPInstance().getLanguage());

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

	private URL getServerURL(TurNLPRequest turNLPRequest) {
		try {
			return new URL(turNLPRequest.getTurNLPInstance().getEndpointURL().concat("/ent"));
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
		return TurSolrField.convertFieldToString(attrValue).replaceAll("[\\n:;]", ". ")
				.replaceAll("\\h|\\r|\\n|\"|'|R\\$", " ")
				.replaceAll("\\.+", ". ").replaceAll(" +", " ").trim();
	}

	public Map<String, List<String>> getAttributes(TurNLPRequest turNLPRequest, Map<String, List<String>> entityList) {
		Map<String, List<String>> entityAttributes = new HashMap<>();

		for (TurNLPEntityRequest turNLPEntityRequest : turNLPRequest.getEntities()) {
			entityAttributes.put(turNLPEntityRequest.getTurNLPVendorEntity().getTurNLPEntity().getInternalName(),
					this.getEntity(turNLPEntityRequest.getName(), entityList));
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
