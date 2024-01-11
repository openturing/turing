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

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.se.result.TurSEResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.solr.common.SolrDocument;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
@Slf4j
public class TurSolrUtils {

	public static final String STR_SUFFIX = "_str";
	public static final String SCHEMA_API_URL = "%s/solr/%s/schema";

	private TurSolrUtils() {
		throw new IllegalStateException("Solr Utility class");
	}

	public static void deleteCore(TurSEInstance turSEInstance, String coreName) {
		TurSolrUtils.deleteCore(getSolrUrl(turSEInstance), coreName);
	}

	private static String getSolrUrl(TurSEInstance turSEInstance) {
		return String.format("http://%s:%s",
				turSEInstance.getHost(),
				turSEInstance.getPort());
	}

	public static void deleteCore(String solrUrl, String name) {
		try (HttpClient client = getHttpClient()) {
			HttpRequest request = getHttpRequestBuilderJson()
					.uri(URI.create(String.format(
							"%s/api/cores?action=UNLOAD&core=%s&deleteIndex=true&deleteDataDir=true&deleteInstanceDir=true",
							solrUrl, name)))
					.GET().build();
			client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
				log.error(e.getMessage(), e);
		}
	}

	public static boolean existsField(TurSEInstance turSEInstance, String coreName, String fieldName) {
		try (HttpClient client = getHttpClient()) {
			HttpRequest request = getHttpRequestBuilderJson()
					.uri(URI.create(String.format("%s/solr/%s/schema/fields/%s",
							getSolrUrl(turSEInstance), coreName, fieldName)))
					.build();
			return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
		} catch (IOException | InterruptedException e) {
			log.error(e.getMessage(), e);
			return false;
		}
	}

	public static void addOrUpdateField(TurSolrFieldAction turSolrFieldAction, TurSEInstance turSEInstance,
										String coreName, String fieldName, TurSEFieldType turSEFieldType,
										boolean stored, boolean multiValued) {
		String json = String.format("""
					{
						"%s":{
							"name": "%s",
							"type": "%s",
							"stored": %s,
							"multiValued": %s
							}
						}
						""", turSolrFieldAction.getSolrAction(), fieldName,
				getSolrFieldType(turSEFieldType), stored, multiValued);
		try (HttpClient client = getHttpClient()) {
			System.out.println("addOrUpdateField: " + json);
				client.send(getHttpRequestSchemaApi(turSEInstance, coreName, json), HttpResponse.BodyHandlers.ofString());
			if (isCreateCopyFieldByCore(turSEInstance, coreName, fieldName, turSEFieldType)) {
				createCopyFieldByCore(turSEInstance, coreName, fieldName, multiValued);
			}
		} catch (IOException | InterruptedException e) {
			log.error(e.getMessage(), e);
		}
	}

	public static boolean isCreateCopyFieldByCore(TurSEInstance turSEInstance, String coreName,
												  String fieldName, TurSEFieldType turSEFieldType) {
		return turSEFieldType.equals(TurSEFieldType.TEXT)
				&& !fieldName.endsWith(STR_SUFFIX)
				&& !existsField(turSEInstance, coreName, fieldName.concat(STR_SUFFIX));
	}

	public static void createCopyFieldByCore(TurSEInstance turSEInstance,
											 String coreName, String fieldName,
											 boolean multiValued) {
		addOrUpdateField(TurSolrFieldAction.ADD, turSEInstance, coreName, fieldName.concat(STR_SUFFIX),
				TurSEFieldType.STRING, true, multiValued);
		String json = String.format("""
					{
				    "add-copy-field":{
				 		 "source":"%s",
				         "dest":[ "%s"]
				 		}
				 	}
				""", fieldName, fieldName.concat(STR_SUFFIX));
		try (HttpClient client = getHttpClient()) {
			HttpRequest request = getHttpRequestSchemaApi(turSEInstance, coreName, json);
			client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			log.error(e.getMessage(), e);
		}

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
	private static String getSolrFieldType(TurSEFieldType turSEFieldType) {
		return switch (turSEFieldType) {
			case TEXT -> "text_general";
			case STRING -> "string";
			case INT -> "pint";
			case BOOL -> "boolean";
			case DATE -> "pdate";
			case LONG -> "plong";
			case ARRAY -> "strings";
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
		String json = String.format("""
					{
				    "create": [
				        {
				            "name": "%s",
				            "instanceDir": "%s",
				            "configSet": "%s"
				        }
				    ]
				}
				""", coreName, coreName, configSet);
		try (HttpClient client = getHttpClient()) {
			HttpRequest request = getHttpRequestBuilderJson()
					.uri(URI.create(String.format("%s/api/cores", solrUrl)))
					.POST(BodyPublishers.ofString(json))
					.build();
				client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
				log.error(e.getMessage(), e);
		}
	}

	public static void createCollection(String solrUrl, String coreName, InputStream inputStream, int shards) {
		try (HttpClient client = getHttpClient()) {
			HttpRequest configSetRequest = HttpRequest.newBuilder()
					.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
					.uri(
								URI.create(String.format("%s/api/cluster/configs/%s", solrUrl, coreName)))
						.PUT(BodyPublishers.ofByteArray(IOUtils.toByteArray(inputStream)))
					.build();
				client.send(configSetRequest, HttpResponse.BodyHandlers.ofString());
			String json = String.format("""
						{
					 	"name": "%s",
					 	"config": "%s",
					 	"numShards": %d
					 	}
					""", coreName, coreName, shards);
			HttpRequest request = getHttpRequestBuilderJson()
					.uri(URI.create(String.format("%s/api/collections", solrUrl)))
					.POST(BodyPublishers.ofString(json))
					.build();
			client.send(request, HttpResponse.BodyHandlers.ofString());
			} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	public static TurSEResult createTurSEResultFromDocument(SolrDocument document) {
		TurSEResult turSEResult = TurSEResult.builder().build();
		document.getFieldNames()
				.forEach(attribute -> turSEResult.getFields().put(attribute, document.getFieldValue(attribute)));
		return turSEResult;
	}

	public static int firstRowPositionFromCurrentPage(TurSEParameters turSEParameters) {
		return (turSEParameters.getCurrentPage() * turSEParameters.getRows()) - turSEParameters.getRows();
	}

	public static int lastRowPositionFromCurrentPage(TurSEParameters turSEParameters) {
		return (turSEParameters.getCurrentPage() * turSEParameters.getRows());
	}
}
