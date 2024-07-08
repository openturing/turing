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

package com.viglet.turing.plugins.nlp.spacy;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.nlp.TurNLPEntityRequest;
import com.viglet.turing.nlp.TurNLPRequest;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPVendorEntity;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;
import com.viglet.turing.plugins.nlp.TurNLPPlugin;
import com.viglet.turing.solr.TurSolrField;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class TurSpaCyConnector implements TurNLPPlugin {

    @Override
    public Map<String, List<String>> processAttributesToEntityMap(TurNLPRequest turNLPRequest) {
        return this.request(turNLPRequest);
    }

    public Map<String, List<String>> request(TurNLPRequest turNLPRequest) {
        Map<String, List<String>> entityList = processAttributes(turNLPRequest, getServerURL(turNLPRequest));
        return this.getAttributes(turNLPRequest, entityList);
    }

    private URL getServerURL(TurNLPRequest turNLPRequest) {
        try {
            return URI.create(turNLPRequest.getTurNLPInstance().getEndpointURL().concat("/ent")).toURL();
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private Map<String, List<String>> processAttributes(TurNLPRequest turNLPRequest, URL serverURL) {
        Map<String, List<String>> entityList = new HashMap<>();
        if (turNLPRequest.getData() != null) {
            for (Object attrValue : turNLPRequest.getData().values()) {
                for (String sentence : createSentences(attrValue)) {

                    processSentence(turNLPRequest, entityList, serverURL, sentence);
                }
            }
        }
        return entityList;
    }

    private void processSentence(TurNLPRequest turNLPRequest, Map<String, List<String>> entityList, URL serverURL,
                                 String sentence) {
        if (log.isDebugEnabled()) {
            log.debug("SpaCy Text: {}", sentence);
        }
        HttpPost httpPost = prepareHttpPost(turNLPRequest, serverURL, sentence);

        try (CloseableHttpClient httpclient = HttpClients.createDefault();
             CloseableHttpResponse response = httpclient.execute(httpPost)) {
            HttpEntity entity = response.getEntity();

            if (entity != null) {
                String jsonResponse = new String(entity.getContent().readAllBytes(), StandardCharsets.UTF_8);
                if (TurCommonsUtils.isJSONValid(jsonResponse)) {
                    if (log.isDebugEnabled()) {
                        log.debug("SpaCy JSONResponse: {}", jsonResponse);
                    }
                    this.getEntities(sentence, new JSONArray(jsonResponse), entityList);
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private HttpPost prepareHttpPost(TurNLPRequest turNLPRequest, URL serverURL, String sentence) {
        JSONObject jsonBody = createJSONRequest(turNLPRequest, sentence);
        HttpPost httpPost = new HttpPost(serverURL.toString());
        httpPost.setHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        httpPost.setHeader(HttpHeaders.ACCEPT_ENCODING, StandardCharsets.UTF_8.name());
        StringEntity stringEntity = new StringEntity(jsonBody.toString(), StandardCharsets.UTF_8);
        httpPost.setEntity(stringEntity);
        return httpPost;
    }

    private String[] createSentences(Object attrValue) {
        return cleanFullText(attrValue).split("\\.");
    }

    private String cleanFullText(Object attrValue) {
        return TurSolrField.convertFieldToString(attrValue).replaceAll("[\\n:;]", ". ")
                .replaceAll("\\h|\\r|\\n|\"|'|R\\$", " ")
                .replaceAll("\\.+", ". ").replaceAll(" +", " ").trim();
    }

    private JSONObject createJSONRequest(TurNLPRequest turNLPRequest, String atributeValue) {
        JSONObject jsonBody = new JSONObject();
        jsonBody.put("text", atributeValue);

        if (turNLPRequest.getTurNLPInstance().getLanguage().equals(TurLocaleRepository.PT_BR)) {
            jsonBody.put("model", "pt_core_news_sm");
        } else {
            jsonBody.put("model", turNLPRequest.getTurNLPInstance().getLanguage());
        }

        ByteBuffer inputBuffer = ByteBuffer.wrap(jsonBody.toString().getBytes());

        Charset utf8Charset = StandardCharsets.UTF_8;
        Charset customCharset = StandardCharsets.UTF_8;

        // decode UTF-8
        CharBuffer data = utf8Charset.decode(inputBuffer);

        // encode
        ByteBuffer outputBuffer = customCharset.encode(data);

        byte[] outputData = new String(outputBuffer.array()).getBytes(StandardCharsets.UTF_8);
        String jsonUTF8 = new String(outputData);

        if (log.isDebugEnabled()) {
            log.debug("SpaCy JSONBody: {}", jsonUTF8);
        }
        return jsonBody;
    }

    public Map<String, List<String>> getAttributes(TurNLPRequest turNLPRequest, Map<String, List<String>> entityList) {
        Map<String, List<String>> entityAttributes = new HashMap<>();

        for (TurNLPEntityRequest turNLPEntityRequest : turNLPRequest.getEntities()) {
            Optional.ofNullable(turNLPEntityRequest)
                    .map(TurNLPEntityRequest::getTurNLPVendorEntity)
                    .map(TurNLPVendorEntity::getTurNLPEntity)
                    .map(TurNLPEntity::getInternalName)
                    .ifPresent(internalName -> entityAttributes.put(internalName,
                            this.getEntity(turNLPEntityRequest.getName(), entityList)));

        }
        if (log.isDebugEnabled()) {
            log.debug("SpaCy getAttributes: {}", entityAttributes);
        }
        return entityAttributes;
    }

    public void getEntities(String text, JSONArray json, Map<String, List<String>> entityList) {

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

            if (log.isDebugEnabled()) {
                log.debug("SpaCy Term (NER): {} ({})", term, label);
            }

            if (add)
                this.handleEntity(label, term, entityList);
        }

    }

    public List<String> getEntity(String entity, Map<String, List<String>> entityList) {
        if (log.isDebugEnabled()) {
            log.debug("getEntity: {}", entity);
        }
        return entityList.get(entity);
    }

    private void handleEntity(String entityType, String entity, Map<String, List<String>> entityList) {
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
}
