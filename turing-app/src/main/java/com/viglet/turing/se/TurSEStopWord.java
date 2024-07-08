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

import com.google.inject.Inject;
import com.viglet.turing.solr.TurSolrInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.schema.AnalyzerDefinition;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.schema.SchemaResponse;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Slf4j
@Component
public class TurSEStopWord {

	private final ResourceLoader resourceloader;

	@Inject
	public TurSEStopWord(ResourceLoader resourceloader) {
		this.resourceloader = resourceloader;
	}

	private static final String TEXT_GENERAL = "text_general";
	private static final String CLASS_FILTER = "class";
	private static final String STOP_WORD_CLASS_FILTER = "solr.StopFilterFactory";
	private static final String WORDS_ATTRIBUTE = "words";
	private static final String DEFAULT_STOP_WORD_FILE = "classpath:/solr/conf/lang/stopwords.txt";
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
				stopwordsStream = getStopWord(turSolrInstance, analyzer);
			} else if (initialFieldTypeResponse.getFieldType().getQueryAnalyzer() != null) {
				AnalyzerDefinition queryAnalyzer = initialFieldTypeResponse.getFieldType().getQueryAnalyzer();
				stopwordsStream = getStopWord(turSolrInstance, queryAnalyzer);
			} else if (initialFieldTypeResponse.getFieldType().getIndexAnalyzer() != null) {
				AnalyzerDefinition indexAnalyzer = initialFieldTypeResponse.getFieldType().getIndexAnalyzer();
				stopwordsStream = getStopWord(turSolrInstance, indexAnalyzer);
			}
		} catch (SolrServerException | IOException e) {
			log.error(e.getMessage(), e);
		}

		if (stopwordsStream == null) {
			try {
				stopwordsStream = resourceloader.getResource(DEFAULT_STOP_WORD_FILE).getInputStream();
			} catch (IOException e) {
				log.error(e.getMessage(), e);
			}
		}
		if (stopwordsStream != null) {
			try (InputStreamReader isr = new InputStreamReader(stopwordsStream);
				 BufferedReader br = new BufferedReader(isr)) {
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
				log.error(e.getMessage(), e);
			}
		}
		return Collections.emptyList();
	}

	private InputStream getStopWord(TurSolrInstance turSolrInstance,
			AnalyzerDefinition analyzer) {
		InputStream stopwordsStream = null;
		if (analyzer.getFilters() != null && !analyzer.getFilters().isEmpty())
			for (Map<String, Object> fieldTypeMap : analyzer.getFilters()) {
				if (fieldTypeMap.get(CLASS_FILTER).equals(STOP_WORD_CLASS_FILTER)) {
					String url = String.format(ADMIN_FILE_URL, turSolrInstance.getSolrUrl().toString(),
							APPLICATION_OCTET_STREAM_UTF8, fieldTypeMap.get(WORDS_ATTRIBUTE));
					ResponseEntity<String> response = new RestTemplate().getForEntity(url, String.class);
					stopwordsStream = IOUtils.toInputStream(Objects.requireNonNull(response.getBody()), StandardCharsets.UTF_8);
				}

			}
		return stopwordsStream;
	}
}
