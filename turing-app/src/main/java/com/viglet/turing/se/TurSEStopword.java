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
package com.viglet.turing.se;

import com.viglet.turing.solr.TurSolrInstance;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.schema.AnalyzerDefinition;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.schema.SchemaResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class TurSEStopword {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	@Autowired
	private ResourceLoader resourceloader;
	private static final String TEXT_GENERAL = "text_general";
	private static final String CLASS_FILTER = "class";
	private static final String STOPWORD_CLASS_FILTER = "solr.StopFilterFactory";
	private static final String WORDS_ATTRIBUTE = "words";
	private static final String DEFAULT_STOPWORD_FILE = "classpath:/solr/conf/lang/stopwords.txt";
	private static final String APPLICATION_OCTET_STREAM_UTF8 = "application/octet-stream;charset:utf-8";
	private static final String ADMIN_FILE_URL = "%s/admin/file?contentType=%s&file=%s";

	public List<String> getStopWords(TurSolrInstance turSolrInstance) {
		InputStream stopwordsStream = null;
		SchemaRequest.FieldType fieldTypeRequest = new SchemaRequest.FieldType(TEXT_GENERAL);
		try {
			SchemaResponse.FieldTypeResponse initialFieldTypeResponse = fieldTypeRequest
					.process(turSolrInstance.getSolrClient());
			if (initialFieldTypeResponse.getFieldType().getAnalyzer() != null) {
				AnalyzerDefinition analyzer = initialFieldTypeResponse.getFieldType().getAnalyzer();
				stopwordsStream = getStopword(turSolrInstance, stopwordsStream, analyzer);
			} else if (initialFieldTypeResponse.getFieldType().getQueryAnalyzer() != null) {
				AnalyzerDefinition queryAnalyzer = initialFieldTypeResponse.getFieldType().getQueryAnalyzer();
				stopwordsStream = getStopword(turSolrInstance, stopwordsStream, queryAnalyzer);
			} else if (initialFieldTypeResponse.getFieldType().getIndexAnalyzer() != null) {
				AnalyzerDefinition indexAnalyzer = initialFieldTypeResponse.getFieldType().getIndexAnalyzer();
				stopwordsStream = getStopword(turSolrInstance, stopwordsStream, indexAnalyzer);
			}
		} catch (SolrServerException | IOException e) {
			logger.error(e.getMessage(), e);
		}

		if (stopwordsStream == null) {
			try {
				stopwordsStream = resourceloader.getResource(DEFAULT_STOPWORD_FILE).getInputStream();
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
		if (stopwordsStream != null) {
			try (InputStreamReader isr = new InputStreamReader(stopwordsStream);
				 BufferedReader br = new BufferedReader(isr);) {
				List<String> stopWords = new ArrayList<>();
				while (br.ready()) {
					String[] line = br.readLine().split("\\|");
					if (line.length == 0) {
						stopWords.add(br.readLine().trim());
					} else {
						stopWords.add(line[0].trim());
					}

				}

				return stopWords;
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
		}
		return Collections.emptyList();
	}

	private InputStream getStopword(TurSolrInstance turSolrInstance, InputStream stopwordsStream,
			AnalyzerDefinition analyzer) {
		if (analyzer.getFilters() != null && !analyzer.getFilters().isEmpty())
			for (Map<String, Object> fieldTypeMap : analyzer.getFilters()) {
				if (fieldTypeMap.get(CLASS_FILTER).equals(STOPWORD_CLASS_FILTER)) {
					String url = String.format(ADMIN_FILE_URL, turSolrInstance.getSolrUrl().toString(),
							APPLICATION_OCTET_STREAM_UTF8, fieldTypeMap.get(WORDS_ATTRIBUTE));
					ResponseEntity<String> response = new RestTemplate().getForEntity(url, String.class);
					stopwordsStream = IOUtils.toInputStream(Objects.requireNonNull(response.getBody()), StandardCharsets.UTF_8);
				}

			}
		return stopwordsStream;
	}
}
