/*
 * Copyright (C) 2016-2023 the original author or authors.
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
package com.viglet.turing.solr;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.collections4.KeyValue;
import org.apache.http.HttpHeaders;
import org.apache.solr.common.SolrDocument;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.http.MediaType;

import com.google.gson.Gson;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.solr.bean.TurSECoreInfo;
import com.viglet.turing.solr.bean.TurSolrFieldBean;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class TurSolrUtils {

    public static final String STR_SUFFIX = "_str";
    public static final String SCHEMA_API_URL = "%s/solr/%s/schema";

    private TurSolrUtils() {
        throw new IllegalStateException("Solr Utility class");
    }

    public static void clearCore(TurSEInstance turSEInstance, String coreName) {
        String json = "{\"delete\":{\"query\":\"*:*\"}}";
        HttpRequest request = getHttpRequestBuilderJson()
                .uri(URI.create(String.format("%s/solr/%s/update?commit=true",
                        getSolrUrl(turSEInstance), coreName)))
                .POST(BodyPublishers.ofString(json))
                .build();
        executeRequest(request, "Failed to clear core: " + coreName);
    }

    private static String getSolrUrl(TurSEInstance turSEInstance) {
        try {
            URI uri = URI.create(turSEInstance.getEndpointUrl());
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (Exception e) {
            return turSEInstance.getEndpointUrl();
        }
    }

    public static void deleteCore(String solrUrl, String name) {
        String uri = String.format(
                "%s/api/cores?action=UNLOAD&core=%s&deleteIndex=true&deleteDataDir=true&deleteInstanceDir=true",
                solrUrl, name);
        HttpRequest request = getHttpRequestBuilderJson()
                .uri(URI.create(uri))
                .GET()
                .build();
        executeRequest(request, "Failed to delete core: " + name);
    }

    public static TurSolrFieldBean getField(TurSEInstance turSEInstance, String coreName, String fieldName) {
        URI uri = getFieldUri(turSEInstance, coreName, fieldName);
        HttpRequest request = getHttpRequestBuilderJson()
                .uri(uri)
                .GET()
                .build();

        return executeRequest(request, "Failed to get field: " + fieldName)
                .filter(response -> response.statusCode() == 200)
                .map(HttpResponse::body)
                .map(JSONObject::new)
                .filter(json -> json.has("field"))
                .map(json -> new Gson().fromJson(json.getJSONObject("field").toString(), TurSolrFieldBean.class))
                .orElse(TurSolrFieldBean.builder().build());
    }

    private static URI getFieldUri(TurSEInstance turSEInstance, String coreName, String fieldName) {
        return URI.create(String.format("%s/solr/%s/schema/fields/%s",
                getSolrUrl(turSEInstance), coreName, fieldName));
    }

    public static boolean existsField(TurSEInstance turSEInstance, String coreName, String fieldName) {
        URI uri = getFieldUri(turSEInstance, coreName, fieldName);
        HttpRequest request = getHttpRequestBuilderJson()
                .uri(uri)
                .GET()
                .build();

        return executeRequest(request, "Failed to check field existence: " + fieldName)
                .map(response -> response.statusCode() == 200)
                .orElse(false);
    }

    public static void addOrUpdateField(TurSolrFieldAction turSolrFieldAction, TurSEInstance turSEInstance,
            String coreName, String fieldName, TurSEFieldType turSEFieldType,
            boolean stored, boolean multiValued) {
        ensureCurrencyFieldTypeByCore(turSEInstance, coreName, turSEFieldType);

        Map<String, Object> fieldDetails = Map.of(
                "name", fieldName,
                "type", getSolrFieldType(turSEFieldType),
                "stored", stored,
                "multiValued", multiValued);

        executeSchemaAction(turSEInstance, coreName, turSolrFieldAction.getSolrAction(), fieldDetails);

        if (isCreateCopyFieldByCore(turSEInstance, coreName, fieldName, turSEFieldType)) {
            createCopyFieldByCore(turSEInstance, coreName, fieldName, multiValued);
        }
    }

    private static void ensureCurrencyFieldTypeByCore(TurSEInstance turSEInstance,
            String coreName, TurSEFieldType turSEFieldType) {
        if (turSEFieldType != TurSEFieldType.CURRENCY) {
            return;
        }

        Map<String, Object> currencyFieldTypeDetails = new LinkedHashMap<>();
        currencyFieldTypeDetails.put("name", "currency");
        currencyFieldTypeDetails.put("class", "solr.CurrencyFieldType");
        currencyFieldTypeDetails.put("amountLongSuffix", "_l_ns");
        currencyFieldTypeDetails.put("codeStrSuffix", "_s_ns");
        currencyFieldTypeDetails.put("defaultCurrency", "USD");
        currencyFieldTypeDetails.put("currencyConfig", "currency.xml");

        executeSchemaAction(turSEInstance, coreName, "add-field-type", currencyFieldTypeDetails);
    }

    public static void deleteField(TurSEInstance turSEInstance,
            String coreName, String fieldName, TurSEFieldType turSEFieldType) {
        Map<String, Object> fieldDetails = Map.of("name", fieldName);

        executeSchemaAction(turSEInstance, coreName, TurSolrFieldAction.DELETE.getSolrAction(), fieldDetails);

        if (isDeleteCopyFieldByCore(turSEInstance, coreName, fieldName, turSEFieldType)) {
            deleteCopyFieldByCore(turSEInstance, coreName, fieldName);
        }
    }

    public static boolean isCreateCopyFieldByCore(TurSEInstance turSEInstance, String coreName,
            String fieldName, TurSEFieldType turSEFieldType) {
        return turSEFieldType.equals(TurSEFieldType.TEXT)
                && !fieldName.endsWith(STR_SUFFIX)
                && !existsField(turSEInstance, coreName, fieldName.concat(STR_SUFFIX));
    }

    public static boolean isDeleteCopyFieldByCore(TurSEInstance turSEInstance, String coreName,
            String fieldName, TurSEFieldType turSEFieldType) {
        return turSEFieldType.equals(TurSEFieldType.TEXT)
                && !fieldName.endsWith(STR_SUFFIX)
                && existsField(turSEInstance, coreName, fieldName.concat(STR_SUFFIX));
    }

    public static void deleteCopyFieldByCore(TurSEInstance turSEInstance,
            String coreName, String fieldName) {
        Map<String, Object> copyFieldDetails = createCopyFieldDetails(fieldName);
        executeSchemaAction(turSEInstance, coreName, TurSolrFieldAction.DELETE_COPY.getSolrAction(), copyFieldDetails);
    }

    public static void createCopyFieldByCore(TurSEInstance turSEInstance,
            String coreName, String fieldName,
            boolean multiValued) {
        String stringFieldName = fieldName.concat(STR_SUFFIX);
        addOrUpdateField(TurSolrFieldAction.ADD, turSEInstance, coreName, stringFieldName,
                TurSEFieldType.STRING, true, multiValued);

        Map<String, Object> copyFieldDetails = createCopyFieldDetails(fieldName);
        executeSchemaAction(turSEInstance, coreName, TurSolrFieldAction.ADD_COPY.getSolrAction(), copyFieldDetails);
    }

    private static Map<String, Object> createCopyFieldDetails(String fieldName) {
        List<String> destinations = List.of(fieldName.concat(STR_SUFFIX));
        return Map.of(
                "source", fieldName,
                "dest", destinations);
    }

    private static HttpRequest getHttpRequestSchemaApi(TurSEInstance turSEInstance, String coreName,
            String publisher) {
        return getHttpRequestBuilderJson()
                .uri(getSchemaUri(turSEInstance, coreName))
                .POST(BodyPublishers.ofString(publisher)).build();
    }

    @NotNull
    private static URI getSchemaUri(TurSEInstance turSEInstance, String coreName) {
        return URI.create(String.format(SCHEMA_API_URL,
                getSolrUrl(turSEInstance), coreName));
    }

    @NotNull
    public static String getSolrFieldType(TurSEFieldType turSEFieldType) {
        return switch (turSEFieldType) {
            case TEXT -> "text_general";
            case STRING -> "string";
            case INT -> "pint";
            case BOOL -> "boolean";
            case DATE -> "pdate";
            case LONG -> "plong";
            case ARRAY -> "strings";
            case FLOAT -> "pfloat";
            case DOUBLE -> "pdouble";
            case CURRENCY -> "currency";
        };
    }

    private static HttpRequest.Builder getHttpRequestBuilderJson() {
        return HttpRequest.newBuilder()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    }

    private static HttpClient getHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static void createCore(String solrUrl, String coreName, String configSet) {
        Map<String, String> coreDetails = Map.of(
                "name", coreName,
                "instanceDir", coreName,
                "configSet", configSet);
        List<Map<String, String>> createList = List.of(coreDetails);
        Map<String, Object> root = Map.of("create", createList);
        String json = new ObjectMapper().writeValueAsString(root);

        HttpRequest request = getHttpRequestBuilderJson()
                .uri(URI.create(String.format("%s/api/cores", solrUrl)))
                .POST(BodyPublishers.ofString(json))
                .build();

        executeRequest(request, "Failed to create core: " + coreName);
    }

    public static void createCollection(String solrUrl, String coreName, InputStream inputStream, int shards) {
        try {
            uploadConfigSet(solrUrl, coreName, inputStream);
            createCollectionFromConfig(solrUrl, coreName, shards);
        } catch (IOException e) {
            log.error("Failed to create collection: {}", coreName, e);
        }
    }

    private static void uploadConfigSet(String solrUrl, String coreName, InputStream inputStream) throws IOException {
        HttpRequest configSetRequest = HttpRequest.newBuilder()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .uri(URI.create(String.format("%s/api/cluster/configs/%s", solrUrl, coreName)))
                .PUT(BodyPublishers.ofByteArray(inputStream.readAllBytes()))
                .build();

        executeRequest(configSetRequest, "Failed to upload config set: " + coreName);
    }

    private static void createCollectionFromConfig(String solrUrl, String coreName, int shards) {
        Map<String, Object> root = Map.of(
                "name", coreName,
                "config", coreName,
                "numShards", shards);
        String json = new ObjectMapper().writeValueAsString(root);

        HttpRequest request = getHttpRequestBuilderJson()
                .uri(URI.create(String.format("%s/api/collections", solrUrl)))
                .POST(BodyPublishers.ofString(json))
                .build();

        executeRequest(request, "Failed to create collection from config: " + coreName);
    }

    public static String getValueFromQuery(String q) {
        return TurCommonsUtils.getKeyValueFromColon(q).map(KeyValue::getValue).orElse(q);
    }

    public static TurSEResult createTurSEResultFromDocument(SolrDocument document) {
        Map<String, Object> fields = new java.util.HashMap<>();
        document.getFieldNames()
                .forEach(attribute -> fields.put(attribute, document.getFieldValue(attribute)));
        return TurSEResult.builder()
                .fields(fields)
                .build();
    }

    public static int firstRowPositionFromCurrentPage(TurSEParameters turSEParameters) {
        // Clamp to 0: a client-supplied page <= 0 would otherwise yield a negative Solr
        // 'start', which Solr rejects with "'start' parameter cannot be negative".
        return Math.max(0,
                (turSEParameters.getCurrentPage() * turSEParameters.getRows()) - turSEParameters.getRows());
    }

    public static int lastRowPositionFromCurrentPage(TurSEParameters turSEParameters) {
        return (turSEParameters.getCurrentPage() * turSEParameters.getRows());
    }

    public static List<TurSECoreInfo> listCores(TurSEInstance turSEInstance) {
        HttpRequest request = getHttpRequestBuilderJson()
                .uri(URI.create(String.format("%s/api/cores", getSolrUrl(turSEInstance))))
                .GET()
                .build();

        return executeRequest(request, "Failed to list cores")
                .filter(response -> response.statusCode() == 200)
                .map(HttpResponse::body)
                .map(body -> {
                    Configuration configuration = Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL)
                            .build();
                    DocumentContext jsonContext = JsonPath.parse(body, configuration);
                    Object statusObj = jsonContext.read("$.status");
                    if (statusObj instanceof Map<?, ?> statusMap) {
                        return statusMap.keySet().stream()
                                .map(Object::toString)
                                .sorted()
                                .map(name -> {
                                    Number numDocsRaw = jsonContext.read("$.status." + name + ".index.numDocs");
                                    long numDocs = numDocsRaw != null ? numDocsRaw.longValue() : 0L;
                                    return new TurSECoreInfo(name, numDocs, Collections.emptyList());
                                })
                                .toList();
                    }
                    return Collections.<TurSECoreInfo>emptyList();
                })
                .orElse(Collections.emptyList());
    }

    public static List<TurSECoreInfo> listCollections(TurSEInstance turSEInstance) {
        HttpRequest request = getHttpRequestBuilderJson()
                .uri(URI.create(String.format("%s/api/collections", getSolrUrl(turSEInstance))))
                .GET()
                .build();

        String solrUrl = getSolrUrl(turSEInstance);
        return executeRequest(request, "Failed to list collections")
                .filter(response -> response.statusCode() == 200)
                .map(HttpResponse::body)
                .map(body -> {
                    Configuration configuration = Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL)
                            .build();
                    DocumentContext jsonContext = JsonPath.parse(body, configuration);
                    Object collectionsObj = jsonContext.read("$.collections");
                    if (collectionsObj instanceof List<?> collectionsList) {
                        return collectionsList.stream()
                                .map(Object::toString)
                                .sorted()
                                .map(name -> new TurSECoreInfo(name,
                                        getCollectionDocCount(solrUrl, name),
                                        Collections.emptyList()))
                                .toList();
                    }
                    return Collections.<TurSECoreInfo>emptyList();
                })
                .orElse(Collections.emptyList());
    }

    private static long getCollectionDocCount(String solrUrl, String collectionName) {
        HttpRequest request = getHttpRequestBuilderJson()
                .uri(URI.create(String.format("%s/solr/%s/select?q=*:*&rows=0",
                        solrUrl, collectionName)))
                .GET()
                .build();
        return executeRequest(request, "Failed to get doc count for collection: " + collectionName)
                .filter(response -> response.statusCode() == 200)
                .map(HttpResponse::body)
                .map(body -> {
                    Configuration configuration = Configuration.builder()
                            .options(Option.DEFAULT_PATH_LEAF_TO_NULL).build();
                    DocumentContext jsonContext = JsonPath.parse(body, configuration);
                    Number numFound = jsonContext.read("$.response.numFound");
                    return numFound != null ? numFound.longValue() : 0L;
                })
                .orElse(0L);
    }

    public static void deleteCollection(String solrUrl, String name) {
        HttpRequest request = getHttpRequestBuilderJson()
                .uri(URI.create(String.format("%s/api/collections/%s", solrUrl, name)))
                .DELETE()
                .build();
        executeRequest(request, "Failed to delete collection: " + name);
    }

    public static boolean collectionExists(TurSEInstance turSEInstance, String collection) {
        HttpRequest request = getHttpRequestBuilderJson()
                .uri(URI.create(String.format("%s/api/collections/%s",
                        getSolrUrl(turSEInstance), collection)))
                .GET()
                .build();

        return executeRequest(request, "Failed to check collection existence: " + collection)
                .map(response -> response.statusCode() == 200)
                .orElse(false);
    }

    public static boolean coreExists(TurSEInstance turSEInstance, String core) {
        HttpRequest request = getHttpRequestBuilderJson()
                .uri(URI.create(String.format("%s/api/cores/%s",
                        getSolrUrl(turSEInstance), core)))
                .GET()
                .build();

        return executeRequest(request, "Failed to check core existence: " + core)
                .filter(response -> response.statusCode() == 200)
                .map(HttpResponse::body)
                .map(body -> parseCoreStatus(body, core))
                .orElse(false);
    }

    private static boolean parseCoreStatus(String body, String core) {
        Configuration configuration = Configuration.builder().options(Option.DEFAULT_PATH_LEAF_TO_NULL).build();
        DocumentContext jsonContext = JsonPath.parse(body, configuration);
        return jsonContext.read("$.status." + core + ".name") != null;
    }

    private static void executeSchemaAction(TurSEInstance turSEInstance, String coreName,
            String action, Map<String, Object> details) {
        Map<String, Object> root = Map.of(action, details);
        String json = new ObjectMapper().writeValueAsString(root);
        HttpRequest request = getHttpRequestSchemaApi(turSEInstance, coreName, json);
        executeRequest(request, "Failed to execute schema action: " + action);
    }

    /**
     * T663 / §XXXIX — PUT the site's synonym mappings into a Solr Managed
     * Synonyms resource (the query analyzer's
     * {@code ManagedSynonymGraphFilterFactory managed="<resource>"} reads it),
     * so a search matches equivalents <em>without reindexing</em> (Algolia's
     * model). The core must be reloaded afterwards ({@link #reloadCore}).
     * Fail-open: a transport/HTTP error is logged and reported as {@code false}.
     *
     * @return {@code true} when the PUT returned a 2xx status
     */
    public static boolean putManagedSynonyms(TurSEInstance turSEInstance, String coreName,
            String managedResource, Map<String, List<String>> mappings) {
        String json = new ObjectMapper().writeValueAsString(mappings);
        URI uri = URI.create(String.format("%s/solr/%s/schema/analysis/synonyms/%s",
                getSolrUrl(turSEInstance), coreName, managedResource));
        HttpRequest request = getHttpRequestBuilderJson()
                .uri(uri)
                .PUT(BodyPublishers.ofString(json))
                .build();
        return executeRequest(request, "Failed to put managed synonyms to core: " + coreName)
                .map(r -> r.statusCode() >= 200 && r.statusCode() < 300)
                .orElse(false);
    }

    /**
     * T663 / §XXXIX — reloads a Solr core via the Core Admin RELOAD action so
     * managed-resource changes (e.g. synonyms just pushed via
     * {@link #putManagedSynonyms}) take effect. Fail-open.
     *
     * @return {@code true} when the reload returned a 2xx status
     */
    public static boolean reloadCore(TurSEInstance turSEInstance, String coreName) {
        URI uri = URI.create(String.format("%s/solr/admin/cores?action=RELOAD&core=%s",
                getSolrUrl(turSEInstance), coreName));
        HttpRequest request = HttpRequest.newBuilder().uri(uri).GET().build();
        return executeRequest(request, "Failed to reload core: " + coreName)
                .map(r -> r.statusCode() >= 200 && r.statusCode() < 300)
                .orElse(false);
    }

    private static Optional<HttpResponse<String>> executeRequest(HttpRequest request, String errorMessage) {
        try (HttpClient client = getHttpClient()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return Optional.of(response);
        } catch (IOException e) {
            log.error("{}: {}", errorMessage, e.getMessage(), e);
            return Optional.empty();
        } catch (InterruptedException e) {
            log.error("{}: {}", errorMessage, e.getMessage(), e);
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
