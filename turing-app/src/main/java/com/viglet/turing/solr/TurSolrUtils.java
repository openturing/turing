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
package com.viglet.turing.solr;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.common.SolrDocument;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.se.result.TurSEResult;

public class TurSolrUtils {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	private TurSolrUtils() {
		throw new IllegalStateException("Solr Utility class");
	}

	public static void createCore(String solrUrl, String name, String instanceDir, String configSet) {
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

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(String.format("%s/api/cores", solrUrl)))
				.POST(BodyPublishers.ofString(String.format(json, name, instanceDir, configSet)))
				.setHeader("Content-Type", "application/json").build();

		try {
			client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException | InterruptedException e) {
			logger.error(e.getMessage(), e);
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
