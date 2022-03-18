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

package com.viglet.turing.se;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.schema.SchemaRequest;
import org.apache.solr.client.solrj.response.schema.SchemaResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.viglet.turing.solr.TurSolrInstance;

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

			for (Map<String, Object> fieldTypeMap : initialFieldTypeResponse.getFieldType().getAnalyzer()
					.getFilters()) {
				if (fieldTypeMap.get(CLASS_FILTER).equals(STOPWORD_CLASS_FILTER)) {
					String url = String.format(ADMIN_FILE_URL, turSolrInstance.getSolrUrl().toString(),
							APPLICATION_OCTET_STREAM_UTF8, fieldTypeMap.get(WORDS_ATTRIBUTE));
					ResponseEntity<String> response = new RestTemplate().getForEntity(url, String.class);
					stopwordsStream = IOUtils.toInputStream(response.getBody(), StandardCharsets.UTF_8);
				}

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
		List<String> stopWords = new ArrayList<>();
		try (InputStreamReader isr = new InputStreamReader(stopwordsStream);
				BufferedReader br = new BufferedReader(isr);) {
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
		return Collections.emptyList();
	}
}
