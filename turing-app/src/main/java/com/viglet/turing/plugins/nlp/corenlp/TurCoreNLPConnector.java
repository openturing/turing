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
import java.nio.charset.Charset;
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
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceEntityRepository;
import com.viglet.turing.plugins.nlp.TurNLPImpl;
import com.viglet.turing.solr.TurSolrField;

@Component
public class TurCoreNLPConnector implements TurNLPImpl {
	static final Logger logger = LogManager.getLogger(TurCoreNLPConnector.class.getName());

	@Autowired
	TurNLPInstanceEntityRepository turNLPInstanceEntityRepository;
	@Autowired
	TurSolrField turSolrField;

	public static int PRETTY_PRINT_INDENT_FACTOR = 4;
	List<TurNLPInstanceEntity> nlpInstanceEntities = null;
	Map<String, List<Object>> entityList = new HashMap<String, List<Object>>();

	TurNLPInstance turNLPInstance = null;

	public void startup(TurNLPInstance turNLPInstance) {
		this.turNLPInstance = turNLPInstance;
		nlpInstanceEntities = turNLPInstanceEntityRepository.findByTurNLPInstanceAndEnabled(turNLPInstance, 1);
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

	public Map<String, Object> request(TurNLPInstance turNLPInstance, Map<String, Object> attributes) {

		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			String props = "{\"tokenize.whitespace\":\"true\",\"annotators\":\"tokenize,ssplit,pos,ner\",\"outputFormat\":\"json\"}";

			String queryParams = String.format("properties=%s", URLEncoder.encode(props, "utf-8"));

			URL serverURL = new URL("http", turNLPInstance.getHost(), turNLPInstance.getPort(), "/?" + queryParams);

			HttpPost httppost = new HttpPost(serverURL.toString());

			for (Object attrValue : attributes.values()) {
				StringEntity stringEntity = new StringEntity(turSolrField.convertFieldToString(attrValue));
				httppost.setEntity(stringEntity);

				HttpResponse response = httpclient.execute(httppost);
				HttpEntity entity = response.getEntity();

				if (entity != null) {
					try (InputStream instream = entity.getContent();
							BufferedReader rd = new BufferedReader(
									new InputStreamReader(instream, Charset.forName("UTF-8")))) {
						String jsonResponse = readAll(rd);
						if (this.isJSONValid(jsonResponse)) {
							this.getEntities(new JSONObject(jsonResponse));
						}
					}
				}
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}

		return this.getAttributes();

	}

	public Map<String, Object> getAttributes() {
		Map<String, Object> entityAttributes = new HashMap<String, Object>();

		for (TurNLPInstanceEntity nlpInstanceEntity : nlpInstanceEntities) {
			entityAttributes.put(nlpInstanceEntity.getTurNLPEntity().getInternalName(),
					this.getEntity(nlpInstanceEntity.getName()));
		}

		// System.out.println(jsonObject.toString());
		logger.debug("CoreNLP getAttributes: " + entityAttributes.toString());
		return entityAttributes;
	}

	public void getEntities(JSONObject json) {
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
				// System.out.println("NER: " + currNeToken);
				String word = token.getString("word");
				// Strip out "O"s completely, makes code below easier to
				// understand
				if (currNeToken.equals("O")) {
					// LOG.debug("Skipping '{}' classified as {}", word,
					// currNeToken);
					if (!prevNeToken.equals("O") && (sb.length() > 0)) {
						handleEntity(prevNeToken, sb, tokenList);
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
					// We're done with the current entity - print it out and
					// reset
					// TODO save this token into an appropriate ADT to return
					// for useful processing..
					this.handleEntity(prevNeToken, sb, tokenList);
					newToken = true;
				}
				prevNeToken = currNeToken;

			}
			if (!newToken && (sb.length() > 0)) {
				this.handleEntity(prevNeToken, sb, tokenList);
			}
		}

	}

	public List<Object> getEntity(String entity) {
		// System.out.println("Entity getEntity: " + entity);
		return entityList.get(entity);
	}

	private void handleEntity(String inKey, StringBuilder inSb, List<EmbeddedToken> inTokens) {
		// System.out.println(inSb + " is a " + inKey);
		inTokens.add(new EmbeddedToken(inKey, inSb.toString()));

		if (entityList.containsKey(inKey)) {
			entityList.get(inKey).add(inSb.toString());
		} else {
			List<Object> valueList = new ArrayList<Object>();
			valueList.add(inSb.toString());
			entityList.put(inKey, valueList);
		}

		// System.out.println("entityList Size:" + entityList.size());
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

	public boolean isJSONValid(String test) {
		try {
			new JSONObject(test);
		} catch (JSONException ex) {
			// edited, to include @Arthur's comment
			// e.g. in case JSONArray is valid as well...
			try {
				new JSONArray(test);
			} catch (JSONException ex1) {
				return false;
			}
		}
		return true;
	}
}
