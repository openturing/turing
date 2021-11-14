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

package com.viglet.turing.plugins.nlp.corenlp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.plugins.nlp.TurNLPPlugin;
import com.viglet.turing.solr.TurSolrField;
import com.viglet.turing.utils.TurUtils;

@Component
public class TurCoreNLPConnector implements TurNLPPlugin {
	private static final Logger logger = LogManager.getLogger(TurCoreNLPConnector.class.getName());

	@Autowired
	private TurSolrField turSolrField;

	@Override
	public Map<String, List<String>> processAttributesToEntityMap(TurNLP turNLP) {
		return this.request(turNLP);
	}

	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}

	public Map<String, List<String>> request(TurNLP turNLP) {
		Map<String, List<String>> entityList = new HashMap<>();
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			String props = "{\"tokenize.whitespace\":\"true\",\"annotators\":\"tokenize,ssplit,pos,ner\",\"outputFormat\":\"json\"}";

			String queryParams = String.format("properties=%s", URLEncoder.encode(props, StandardCharsets.UTF_8));

			URL serverURL = new URL("http", turNLP.getTurNLPInstance().getHost(), turNLP.getTurNLPInstance().getPort(), "/?" + queryParams);

			HttpPost httppost = new HttpPost(serverURL.toString());

			for (Object attrValue : turNLP.getAttributeMapToBeProcessed().values()) {
				StringEntity stringEntity = new StringEntity(turSolrField.convertFieldToString(attrValue));
				httppost.setEntity(stringEntity);

				HttpResponse response = httpclient.execute(httppost);
				HttpEntity entity = response.getEntity();

				if (entity != null) {
					try (InputStream instream = entity.getContent();
							BufferedReader rd = new BufferedReader(
									new InputStreamReader(instream, StandardCharsets.UTF_8))) {
						String jsonResponse = readAll(rd);
						if (TurUtils.isJSONValid(jsonResponse)) {
							this.getEntities(new JSONObject(jsonResponse), entityList);
						}
					}
				}
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		
		return this.generateEntityMapFromSentenceTokens(turNLP, entityList);

	}

	public Map<String, List<String>> generateEntityMapFromSentenceTokens(TurNLP turNLP, Map<String, List<String>> entityList) {
		Map<String, List<String>> entityAttributes = new HashMap<>();

		for (TurNLPInstanceEntity nlpInstanceEntity : turNLP.getNlpInstanceEntities()) {
			entityAttributes.put(nlpInstanceEntity.getTurNLPEntity().getInternalName(),
					this.getEntity(entityList, nlpInstanceEntity.getName()));
		}

		logger.debug("CoreNLP getAttributes: {}", entityAttributes);
		return entityAttributes;
	}

	public void getEntities(JSONObject json, Map<String, List<String>> entityList) {
		JSONArray sentences = json.getJSONArray("sentences");

		StringBuilder sb = new StringBuilder();
		List<EmbeddedToken> tokenList = new ArrayList<>();

		for (int i = 0; i < sentences.length(); i++) {
			JSONObject sentence = (JSONObject) sentences.get(i);
			JSONArray tokens = sentence.getJSONArray("tokens");

			// traversing the words in the current sentence, "O" is a sensible
			// default to initialize
			// tokens to since we're not interested in unclassified / unknown
			// things..
			String prevNeToken = "O";
			String currNeToken = "O";
			boolean newToken = true;
		
			for (int t = 0; t < tokens.length(); t++) {

				JSONObject token = (JSONObject) tokens.get(t);

				currNeToken = token.getString("ner");
				String word = token.getString("word");
				if (currNeToken.equals("O")) {

					if (!prevNeToken.equals("O") && (sb.length() > 0)) {
						handleEntity(entityList, prevNeToken, sb, tokenList);
						newToken = true;
					}
					continue;
				}

				if (newToken) {
					prevNeToken = currNeToken;
					newToken = false;
					sb.append(word);
					continue;
				}

				if (currNeToken.equals(prevNeToken)) {
					sb.append(" " + word);
				} else {
					this.handleEntity(entityList, prevNeToken, sb, tokenList);
					newToken = true;
				}
				prevNeToken = currNeToken;

			}
			if (!newToken && (sb.length() > 0)) {
				this.handleEntity(entityList, prevNeToken, sb, tokenList);
			}
		}

	}

	public List<String> getEntity(Map<String, List<String>> entityList, String entity) {
		return entityList.get(entity);
	}

	private void handleEntity(Map<String, List<String>> entityList , String inKey, StringBuilder inSb, List<EmbeddedToken> inTokens) {
		
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

	class EmbeddedToken {

		private String name;
		private String value;

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
}
