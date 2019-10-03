/*
 * Copyright (C) 2016-2019 the original author or authors. 
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

package com.viglet.turing.converse;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.converse.intent.TurConverseContext;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.model.converse.intent.TurConversePhrase;
import com.viglet.turing.persistence.model.converse.intent.TurConverseResponse;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseContextRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePhraseRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseResponseRepository;
import com.viglet.turing.solr.TurSolr;

@Component
public class TurConverseIndex {
	static final Logger logger = LogManager.getLogger(TurConverseIndex.class.getName());
	@Autowired
	TurSolr turSolr;
	@Autowired
	CloseableHttpClient closeableHttpClient;
	@Autowired
	TurConversePhraseRepository turConversePhraseRepository;
	@Autowired
	TurConverseResponseRepository turConverseResponseRepository;
	@Autowired
	TurConverseContextRepository turConverseContextRepository;
	
	public void index(TurConverseIntent turConverseIntent) {
		turConverseIntent.setContextInputs(
				turConverseContextRepository.findByIntentInputs(new HashSet<>(Arrays.asList(turConverseIntent))));
		turConverseIntent.setContextOutputs(
				turConverseContextRepository.findByIntentOutputs(new HashSet<>(Arrays.asList(turConverseIntent))));
		turConverseIntent.setPhrases(turConversePhraseRepository.findByIntent(turConverseIntent));
		turConverseIntent.setResponses(turConverseResponseRepository.findByIntent(turConverseIntent));
		
		TurSEInstance turSEInstance = new TurSEInstance();
		turSEInstance.setHost("localhost");
		turSEInstance.setPort(8983);
		String core = "converse";

		SolrClient solrClient = null;
		String urlString = "http://" + turSEInstance.getHost() + ":" + turSEInstance.getPort() + "/solr/" + core;
		solrClient = new HttpSolrClient.Builder(urlString).withHttpClient(closeableHttpClient)
				.withConnectionTimeout(30000).withSocketTimeout(30000).build();

		SolrInputDocument document = new SolrInputDocument();

		document.addField("id", turConverseIntent.getId());
		document.addField("agent", turConverseIntent.getAgent().getId());
		document.addField("type", "Intent");
		document.addField("name", turConverseIntent.getName());
		for (TurConverseContext contextInput : turConverseIntent.getContextInputs())
		document.addField("contextInput", contextInput.getText());
		
		for (TurConverseContext contextOutput : turConverseIntent.getContextOutputs())
			document.addField("contextOutput", contextOutput.getText());
			
		for (TurConversePhrase phrase : turConverseIntent.getPhrases())
			document.addField("phrases", phrase.getText());

		for (TurConverseResponse response : turConverseIntent.getResponses())
			document.addField("responses", response.getText());

		try {
			solrClient.add(document);
			solrClient.commit();
		} catch (SolrServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
