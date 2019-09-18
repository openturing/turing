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

package com.viglet.turing.api.converse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.bean.converse.TurConverseAgentResponse;
import com.viglet.turing.persistence.model.converse.TurConverseIntent;
import com.viglet.turing.persistence.model.converse.TurConversePhrase;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.repository.converse.TurConversePhraseRepository;
import com.viglet.turing.solr.TurSolr;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/converse/agent")
@Api(tags = "Converse Intent", description = "Converse Intent API")
public class TurConverseAgentAPI {

	@Autowired
	private TurConversePhraseRepository turConversePhraseRepository;
	@Autowired
	TurSolr turSolr;
	@Autowired
	CloseableHttpClient closeableHttpClient;

	@ApiOperation(value = "Converse Intent List")
	@GetMapping("/try")
	public TurConverseAgentResponse turConverseAgentTry(@RequestParam(required = false, name = "q") String q) {
		TurSEInstance turSEInstance = new TurSEInstance();
		turSEInstance.setHost("localhost");
		turSEInstance.setPort(8983);
		String core = "converse";

		SolrClient solrClient = null;
		String urlString = "http://" + turSEInstance.getHost() + ":" + turSEInstance.getPort() + "/solr/" + core;
		solrClient = new HttpSolrClient.Builder(urlString).withHttpClient(closeableHttpClient)
				.withConnectionTimeout(30000).withSocketTimeout(30000).build();
		SolrQuery query = new SolrQuery();
		query.setQuery("phrases:\"" + q + "\"");
		TurConverseAgentResponse turConverseAgentResponse = new TurConverseAgentResponse();
		try {
			QueryResponse queryResponse = solrClient.query(query);
			SolrDocumentList results = queryResponse.getResults();
			if (!results.isEmpty()) {
				SolrDocument firstResult = results.get(0);
				@SuppressWarnings("unchecked")
				ArrayList<String> responses = (ArrayList<String>) firstResult.getFieldValue("responses");
				int rnd = new Random().nextInt(responses.size());
				turConverseAgentResponse.setResponse(responses.get(rnd).toString());
				turConverseAgentResponse.setIntent(firstResult.getFieldValue("name").toString());
			}
		} catch (SolrServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		/*
		 * Set<TurConversePhrase> phrases = turConversePhraseRepository.findByText(q);
		 * 
		 * TurConverseAgentResponse turConverseAgentResponse = new
		 * TurConverseAgentResponse(); if (!phrases.isEmpty()) { TurConverseIntent
		 * intent = phrases.iterator().next().getIntent();
		 * turConverseAgentResponse.setResponse(intent.getResponses().iterator().next().
		 * getText()); turConverseAgentResponse.setIntent(intent.getName()); }
		 */
		return turConverseAgentResponse;
	}

}
