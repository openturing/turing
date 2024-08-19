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
import com.viglet.turing.plugins.nlp.TurNLPPlugin;
import com.viglet.turing.plugins.nlp.utils.TurNLPPluginUtils;
import com.viglet.turing.solr.TurSolrField;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONObject;
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
        return this.getAttributes(turNLPRequest, processAttributes(turNLPRequest, getServerURL(turNLPRequest)));
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
        return Optional.ofNullable(turNLPRequest.getData()).map(request -> {
            Map<String, List<String>> entityList = new HashMap<>();
            turNLPRequest.getData().values().forEach(attrValue ->
                    Arrays.stream(createSentences(attrValue)).forEach(sentence ->
                            processSentence(turNLPRequest, entityList, serverURL, sentence)));
            return entityList;
        }).orElseGet(Collections::emptyMap);
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
        return TurNLPPluginUtils.getHttpPost(serverURL, jsonBody);
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
        jsonBody.put("model", turNLPRequest.getTurNLPInstance().getLanguage());
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
        turNLPRequest.getEntities().forEach(entity -> Optional.ofNullable(entity)
                .map(TurNLPEntityRequest::getTurNLPVendorEntity)
                .map(TurNLPVendorEntity::getTurNLPEntity)
                .map(TurNLPEntity::getInternalName)
                .ifPresent(internalName ->
                        entityAttributes.put(internalName, entityList.get(entity.getName()))));
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
                label = "ORG";
                if (!Character.isUpperCase(term.charAt(0)))
                    add = false;
            }
            if (label.equals("PER"))
                label = "PERSON";

            if (log.isDebugEnabled()) {
                log.debug("SpaCy Term (NER): {} ({})", term, label);
            }

            if (add)
                TurNLPPluginUtils.handleEntity(label, term, entityList);
        }

    }
}
