/*
 * Copyright (C) 2016-2022 the original author or authors. 
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

package com.viglet.turing.plugins.nlp.gcp;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.plugins.nlp.TurNLPPlugin;
import com.viglet.turing.plugins.nlp.gcp.request.TurNLPGCPDocumentRequest;
import com.viglet.turing.plugins.nlp.gcp.request.TurNLPGCPEncodingResponse;
import com.viglet.turing.plugins.nlp.gcp.request.TurNLPGCPRequest;
import com.viglet.turing.plugins.nlp.gcp.request.TurNLPGCPTypeResponse;
import com.viglet.turing.plugins.nlp.gcp.response.TurNLPGCPEntityResponse;
import com.viglet.turing.plugins.nlp.gcp.response.TurNLPGCPResponse;
import com.viglet.turing.solr.TurSolrField;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
@Component
public class TurNLPGCPConnector implements TurNLPPlugin {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	@Override
	public Map<String, List<String>> processAttributesToEntityMap(TurNLP turNLP) {
		return this.request(turNLP);
	}

	public Map<String, List<String>> request(TurNLP turNLP) {
		Map<String, Set<String>> entityList = new HashMap<>();
		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			URL serverURL = new URL(String.format("%s?key=%s", turNLP.getTurNLPInstance().getEndpointURL(),
					turNLP.getTurNLPInstance().getKey()));
			HttpPost httppost = new HttpPost(serverURL.toString());
			for (Object attrValue : turNLP.getAttributeMapToBeProcessed().values()) {
				TurNLPGCPDocumentRequest turNLPGCPDocumentRequest = new TurNLPGCPDocumentRequest(
						TurNLPGCPTypeResponse.PLAIN_TEXT, turNLP.getTurNLPInstance().getLanguage(),
						TurSolrField.convertFieldToString(attrValue));
				TurNLPGCPRequest turNLPGCPRequest = new TurNLPGCPRequest(turNLPGCPDocumentRequest,
						TurNLPGCPEncodingResponse.UTF8);
				httppost.setEntity(new StringEntity(
						new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(turNLPGCPRequest)));
				updateEntityList(httpclient.execute(httppost).getEntity(), entityList);
			}
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}

		return this.generateEntityMapFromSentenceTokens(turNLP, entityList);

	}

	private Map<String, Set<String>> updateEntityList(HttpEntity entity, Map<String, Set<String>> entityList) {
		if (entity != null) {
			String jsonResponse;
			try {
				jsonResponse = new String(entity.getContent().readAllBytes(), StandardCharsets.UTF_8);
				if (TurCommonsUtils.isJSONValid(jsonResponse)) {
					this.getEntities(new ObjectMapper().readValue(jsonResponse, TurNLPGCPResponse.class), entityList);
				}
			} catch (UnsupportedOperationException | IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
		return entityList;
	}

	public Map<String, List<String>> generateEntityMapFromSentenceTokens(TurNLP turNLP,
			Map<String, Set<String>> entityList) {
		Map<String, List<String>> entityAttributes = new HashMap<>();
		turNLP.getNlpInstanceEntities().forEach(
				nlpInstanceEntity -> entityAttributes.put(nlpInstanceEntity.getTurNLPEntity().getInternalName(),
						entityList.get(nlpInstanceEntity.getName()) != null
								? List.copyOf(entityList.get(nlpInstanceEntity.getName()))
								: new ArrayList<>()));

		logger.debug("CGP NLP getAttributes: {}", entityAttributes);
		return entityAttributes;
	}

	public void getEntities(TurNLPGCPResponse turNLPGCPResponse, Map<String, Set<String>> entityList) {
		turNLPGCPResponse.getEntities().forEach(entity -> handleEntity(entityList, entity));
	}

	private void handleEntity(Map<String, Set<String>> entityList, TurNLPGCPEntityResponse entity) {
		if (entityList.containsKey(entity.getType().toString())) {
			entityList.get(entity.getType().toString()).add(entity.getName().toLowerCase().trim());
		} else {
			Set<String> valueList = new HashSet<>();
			valueList.add(entity.getName().toLowerCase().trim());
			entityList.put(entity.getType().toString(), valueList);
		}

	}
}
