/*
 * Copyright (C) 2016-2020 the original author or authors. 
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.TransformerException;

import org.apache.http.HttpEntity;
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
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;
import com.viglet.turing.plugins.nlp.TurNLPImpl;
import com.viglet.turing.solr.TurSolrField;

import java.util.*;

@Component
public class TurPolyglotConnector implements TurNLPImpl {
	static final Logger logger = LogManager.getLogger(TurPolyglotConnector.class);

	@Autowired
	TurNLPEntityRepository turNLPEntityRepository;
	@Autowired
	TurSolrField turSolrField;
	private String encoding = "UTF-8";

	public static int PRETTY_PRINT_INDENT_FACTOR = 4;
	List<TurNLPEntity> nlpEntities = null;
	Map<String, List<Object>> entityList = new HashMap<String, List<Object>>();

	TurNLPInstance turNLPInstance = null;

	public void startup(TurNLPInstance turNLPInstance) {
		this.turNLPInstance = turNLPInstance;

		nlpEntities = turNLPEntityRepository.findAll();
	}

	public Map<String, Object> retrieve(Map<String, Object> attributes) throws TransformerException, Exception {
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

	public Map<String, Object> request(TurNLPInstance turNLPInstance, Map<String, Object> attributes)
			throws MalformedURLException, IOException, JSONException {

		URL serverURL = new URL("http", turNLPInstance.getHost(), turNLPInstance.getPort(), "/ent");
		if (logger.isDebugEnabled()) {
			logger.debug("URL:" + serverURL.toString());
		}

		CloseableHttpClient httpclient = HttpClients.createDefault();
		try {
			HttpPost httpPost = new HttpPost(serverURL.toString());
			Charset utf8Charset = Charset.forName("UTF-8");
			Charset customCharset = Charset.forName(encoding);

			if (attributes != null) {
				for (Object attrValue : attributes.values()) {
					JSONObject jsonBody = new JSONObject();
					String atributeValueFullText = removeUrl(turSolrField.convertFieldToString(attrValue))
							.replaceAll("\\n|:|;", ". ").replaceAll("\\h|\\r|\\n|\"|\'|R\\$", " ").replaceAll("â€�", " ")
							.replaceAll("â€œ", " ").replaceAll("\\.+", ". ").replaceAll(" +", " ").trim();

					for (String atributeValue : atributeValueFullText.split("\\.")) {
						if (logger.isDebugEnabled()) {
							logger.debug("Polyglot Text: " + atributeValue);
						}
						jsonBody.put("text", atributeValue);
		
						jsonBody.put("model", turNLPInstance.getLanguage());
				

						ByteBuffer inputBuffer = ByteBuffer.wrap(jsonBody.toString().getBytes());

						// decode UTF-8
						CharBuffer data = utf8Charset.decode(inputBuffer);

						// encode
						ByteBuffer outputBuffer = customCharset.encode(data);

						byte[] outputData = new String(outputBuffer.array()).getBytes("UTF-8");
						String jsonUTF8 = new String(outputData);

						if (logger.isDebugEnabled()) {
							logger.debug("Polyglot JSONBody: " + jsonUTF8);
						}
						httpPost.setHeader("Accept", "application/json");
						httpPost.setHeader("Content-type", "application/json");
						httpPost.setHeader("Accept-Encoding", "UTF-8");
						StringEntity stringEntity = new StringEntity(new String(jsonBody.toString()), "UTF-8");
						httpPost.setEntity(stringEntity);

						CloseableHttpResponse response = httpclient.execute(httpPost);
						try {
							HttpEntity entity = response.getEntity();

							if (entity != null) {
								InputStream instream = entity.getContent();
								BufferedReader rd = new BufferedReader(
										new InputStreamReader(instream, Charset.forName("UTF-8")));
								String jsonResponse = readAll(rd);
								if (this.isJSONValid(jsonResponse)) {
									if (logger.isDebugEnabled()) {
										logger.debug("Polyglot JSONResponse: " + jsonResponse);
									}
									this.getEntities(atributeValue, new JSONArray(jsonResponse));
								}
								try {
								} finally {
									instream.close();
								}
							}
						} finally {
							response.close();
						}
					}
				}

			}
		} finally {
			httpclient.close();
		}
		return this.getAttributes();

	}

	public Map<String, Object> getAttributes() throws JSONException {
		Map<String, Object> entityAttributes = new HashMap<String, Object>();

		for (TurNLPEntity nlpEntity : nlpEntities) {
			entityAttributes.put(nlpEntity.getInternalName(), this.getEntity(nlpEntity.getInternalName()));
		}

		// System.out.println(jsonObject.toString());
		if (logger.isDebugEnabled()) {
			logger.debug("Polyglot getAttributes: " + entityAttributes.toString());
		}
		return entityAttributes;
	}

	public void getEntities(String text, JSONArray json) throws JSONException {

		for (int i = 0; i < json.length(); i++) {
			boolean add = true;
			JSONObject token = (JSONObject) json.get(i);

			String term = token.getString("token");
			String label = token.getString("label");

			if (label.equals("NAME"))
				add = false;

			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Polyglot Term (NER): %s (%s)", term, label));
			}

			if (add)
				this.handleEntity(label, term);
		}

	}

	public List<Object> getEntity(String entity) {
		if (logger.isDebugEnabled()) {
			logger.debug(String.format("getEntity: %s", entity));
		}
		return entityList.get(entity);
	}

	private void handleEntity(String entityType, String entity) {
		if (entityList.containsKey(entityType)) {
			if (!entityList.get(entityType).contains(entity) && entity.trim().length() > 1) {
				entityList.get(entityType).add(entity.trim());
			}
		} else {
			List<Object> valueList = new ArrayList<Object>();
			valueList.add(entity.trim());
			entityList.put(entityType, valueList);
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

	private String removeUrl(String commentstr) {
		String urlPattern = "((https?|ftp|gopher|telnet|file|Unsure):((//)|(\\\\))+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)";
		Pattern p = Pattern.compile(urlPattern, Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(commentstr);
		int i = 0;
		while (m.find()) {
			commentstr = commentstr.replaceAll(m.group(i), "").trim();
			i++;
		}
		return commentstr;
	}
}
