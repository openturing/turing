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
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.se.result.TurSEResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.solr.common.SolrDocument;
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

	private TurSolrUtils() {
		throw new IllegalStateException("Solr Utility class");
	}

	public static void deleteCore(TurSEInstance turSEInstance, String coreName) {
		TurSolrUtils.deleteCore(getSolrUrl(turSEInstance), coreName);
	}

	private static String getSolrUrl(TurSEInstance turSEInstance) {
		return String.format("http://%s:%s", turSEInstance.getHost(), turSEInstance.getPort());
	}

	public static void deleteCore(String solrUrl, String name) {
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(String.format(
						"%s/api/cores?action=UNLOAD&core=%s&deleteIndex=true&deleteDataDir=true&deleteInstanceDir=true",
						solrUrl, name)))
				.GET().setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();

		try {
			client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			log.error(e.getMessage(), e);
		}
	}

	public static void addField(TurSEInstance turSEInstance, String coreName, String fieldName, String type, boolean multiValued) {
		addOrUpdateField("replace-field", turSEInstance, coreName, fieldName, type, multiValued);
	}

	public static void updateField(TurSEInstance turSEInstance, String coreName, String fieldName, String type,
			boolean multiValued) {
		addOrUpdateField("add-field", turSEInstance, coreName, fieldName, type, multiValued);
	}

	public static void addOrUpdateField(String action, TurSEInstance turSEInstance, String coreName, String fieldName, String type,
			boolean multiValued) {
		String json = """
					{
				    "%s":{
				 		"name": "%s",
				 		"type": "%s",
				 		"stored": true,
				 		"multiValued": %s
				 		}
				 	}
				""";
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(String.format("%s/solr/%s/schema", getSolrUrl(turSEInstance), coreName)))
				.POST(BodyPublishers.ofString(String.format(json, action, fieldName, type, multiValued)))
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();

		try {
			client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			log.error(e.getMessage(), e);
		}
	}

	public static void deleteField(TurSEInstance turSEInstance, String coreName, String fieldName) {
		String json = """
					{
				    "delete-field":{
				 		"name": "%s"
				 		}
				 	}
				""";
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(String.format("%s/solr/%s/schema", getSolrUrl(turSEInstance), coreName)))
				.POST(BodyPublishers.ofString(String.format(json, fieldName)))
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();

		try {
			client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			log.error(e.getMessage(), e);
		}
	}

	public static void createCore(String solrUrl, String coreName, String configSet) {
		String json = """
					{
				    "create": [
				        {
				            "name": "%s",
				            "instanceDir": "%s",
				            "configSet": "%s"
				        }
				    ]
				}
				""";
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(String.format("%s/api/cores", solrUrl)))
				.POST(BodyPublishers.ofString(String.format(json, coreName, coreName, configSet)))
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();

		try {
			client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			log.error(e.getMessage(), e);
		}
	}
	public static void createCollection(String solrUrl, String coreName, InputStream inputStream) {
		HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

		try {
			HttpRequest configSetRequest = HttpRequest.newBuilder().uri(URI.create(String.format("%s/api/cluster/configs/%s", solrUrl, coreName)))
					.PUT(BodyPublishers.ofByteArray(IOUtils.toByteArray(inputStream)))
					.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE).build();
			client.send(configSetRequest, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        String json = """
					{
				 	"name": "%s",
				 	"config": "%s",
				 	"numShards": 1
				 	}
				""";


		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(String.format("%s/api/collections", solrUrl)))
				.POST(BodyPublishers.ofString(String.format(json, coreName, coreName)))
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();

		try {
			client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			log.error(e.getMessage(), e);
		}
	}
	public static TurSEResult createTurSEResultFromDocument(SolrDocument document) {
		TurSEResult turSEResult = new TurSEResult();
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
