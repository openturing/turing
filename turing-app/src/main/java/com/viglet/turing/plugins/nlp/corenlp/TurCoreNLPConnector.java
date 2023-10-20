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
package com.viglet.turing.plugins.nlp.corenlp;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.nlp.TurNLPEntityRequest;
import com.viglet.turing.nlp.TurNLPRequest;
import com.viglet.turing.plugins.nlp.TurNLPPlugin;
import com.viglet.turing.solr.TurSolrField;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TurCoreNLPConnector implements TurNLPPlugin {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	@Override
	public Map<String, List<String>> processAttributesToEntityMap(TurNLPRequest turNLPRequest) {
		return this.request(turNLPRequest);
	}

	public Map<String, List<String>> request(TurNLPRequest turNLPRequest) {
		Map<String, List<String>> entityList = new HashMap<>();
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			String props = "{\"tokenize.whitespace\":\"true\",\"annotators\":\"tokenize,ssplit,pos,ner\",\"outputFormat\":\"json\"}";

			String queryParams = String.format("properties=%s", URLEncoder.encode(props, StandardCharsets.UTF_8));

			URL serverURL = new URL(
					String.format("%s/?%s", turNLPRequest.getTurNLPInstance().getEndpointURL(), queryParams));

			HttpPost httppost = new HttpPost(serverURL.toString());

			for (Object attrValue : turNLPRequest.getData().values()) {
				StringEntity stringEntity = new StringEntity(TurSolrField.convertFieldToString(attrValue));
				httppost.setEntity(stringEntity);

				HttpResponse response = httpclient.execute(httppost);
				HttpEntity entity = response.getEntity();

				if (entity != null) {
					String jsonResponse = new String(entity.getContent().readAllBytes(), StandardCharsets.UTF_8);
					if (TurCommonsUtils.isJSONValid(jsonResponse)) {
						this.getEntities(new JSONObject(jsonResponse), entityList);
					}
				}
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}

		return this.generateEntityMapFromSentenceTokens(turNLPRequest, entityList);

	}

	public Map<String, List<String>> generateEntityMapFromSentenceTokens(TurNLPRequest turNLPRequest,
			Map<String, List<String>> entityList) {
		Map<String, List<String>> entityAttributes = new HashMap<>();

		for (TurNLPEntityRequest turNLPEntityRequest : turNLPRequest.getEntities()) {
			entityAttributes.put(turNLPEntityRequest.getTurNLPVendorEntity().getTurNLPEntity().getInternalName(),
					entityList.get(turNLPEntityRequest.getName()));
		}

		logger.debug("CoreNLP getAttributes: {}", entityAttributes);
		return entityAttributes;
	}

	public void getEntities(JSONObject json, Map<String, List<String>> entityList) {
		JSONArray sentences = json.getJSONArray("sentences");

		StringBuilder sb = new StringBuilder();
		List<EmbeddedToken> tokenList = new ArrayList<>();

		sentences.forEach(obj -> {
			JSONObject sentence = (JSONObject) obj;
			detectEntityFromSentence(entityList, sb, tokenList, sentence);
		});
	}

	private void detectEntityFromSentence(Map<String, List<String>> entityList, StringBuilder sb,
			List<EmbeddedToken> tokenList, JSONObject sentence) {

		JSONArray tokens = sentence.getJSONArray("tokens");
		TokenPositon tokenPositon = new TokenPositon();
		tokens.forEach(obj -> {
			JSONObject token = (JSONObject) obj;
			tokenPositon.setCurrent(token.getString("ner"));
			String word = token.getString("word");
			if (tokenPositon.getCurrent().equals("O")) {
				if (!tokenPositon.getPrevious().equals("O") && (!sb.isEmpty())) {
					handleEntity(entityList, tokenPositon.getPrevious(), sb, tokenList);
					tokenPositon.setNewToken(true);
				}
				return;
			}

			if (tokenPositon.isNewToken()) {
				tokenPositon.setPrevious(tokenPositon.getCurrent());
				tokenPositon.setNewToken(false);
				sb.append(word);
				return;
			}

			if (tokenPositon.getCurrent().equals(tokenPositon.getPrevious())) {
				sb.append(" ").append(word);
			} else {
				this.handleEntity(entityList, tokenPositon.getPrevious(), sb, tokenList);
				tokenPositon.setNewToken(true);
			}
			tokenPositon.setPrevious(tokenPositon.getCurrent());

		});
		if (!tokenPositon.isNewToken() && (!sb.isEmpty())) {
			this.handleEntity(entityList, tokenPositon.getPrevious(), sb, tokenList);
		}
	}

	private void handleEntity(Map<String, List<String>> entityList, String inKey, StringBuilder inSb,
			List<EmbeddedToken> inTokens) {

		inTokens.add(new EmbeddedToken(inKey, inSb.toString()));

		if (entityList.containsKey(inKey)) {
			entityList.get(inKey).add(inSb.toString());
		} else {
			List<String> valueList = new ArrayList<>();
			valueList.add(inSb.toString());
			entityList.put(inKey, valueList);
		}

		inSb.setLength(0);
	}

	static class EmbeddedToken {

		private final String name;
		private final String value;

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}

		public EmbeddedToken(String name, String value) {
			super();
			this.name = name;
			this.value = value;
		}
	}

	static class TokenPositon {
		private String current;
		private String previous;
		private boolean newToken;

		public TokenPositon() {
			super();
			this.current = "O";
			this.previous = "O";
			this.newToken = true;
		}

		public String getCurrent() {
			return current;
		}

		public void setCurrent(String current) {
			this.current = current;
		}

		public String getPrevious() {
			return previous;
		}

		public void setPrevious(String previous) {
			this.previous = previous;
		}

		public boolean isNewToken() {
			return newToken;
		}

		public void setNewToken(boolean newToken) {
			this.newToken = newToken;
		}

	}
}
