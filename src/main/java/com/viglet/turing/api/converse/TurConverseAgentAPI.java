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
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.servlet.http.HttpSession;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.bean.converse.TurConverseAgentResponse;
import com.viglet.turing.converse.TurConverseIndex;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.repository.converse.TurConverseAgentRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseIntentRepository;
import com.viglet.turing.solr.TurSolr;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@RestController
@RequestMapping("/api/converse/agent")
@Api(tags = "Converse Intent", description = "Converse Intent API")
public class TurConverseAgentAPI {

	@Autowired
	private TurConverseAgentRepository turConverseAgentRepository;
	@Autowired
	private TurConverseIntentRepository turConverseIntentRepository;
	@Autowired
	private TurConverseIndex turConverseIndex;
	@Autowired
	private TurSolr turSolr;

	private SolrClient solrClient;

	@ApiOperation(value = "Converse Agent List")
	@GetMapping
	public List<TurConverseAgent> turConverseAgentList() {
		return this.turConverseAgentRepository.findAll();
	}

	@ApiOperation(value = "Show a Converse Agent")
	@GetMapping("/{id}")
	public TurConverseAgent turConverseAgentGet(@PathVariable String id) {
		TurConverseAgent turConverseAgent = turConverseAgentRepository.findById(id).get();
		return turConverseAgent;
	}

	@ApiOperation(value = "Show a Converse Agent")
	@GetMapping("/{id}/intents")
	public Set<TurConverseIntent> turConverseAgentIntentsGet(@PathVariable String id) {
		TurConverseAgent turConverseAgent = turConverseAgentRepository.findById(id).get();
		return turConverseIntentRepository.findByAgent(turConverseAgent);
	}

	@ApiOperation(value = "Create a Converse Agent")
	@PostMapping
	public TurConverseAgent turConverseAgentAdd(@RequestBody TurConverseAgent turConverseAgent) {
		return turConverseAgentRepository.save(turConverseAgent);
	}

	@ApiOperation(value = "Update a Converse Agent")
	@PutMapping("/{id}")
	public TurConverseAgent turConverseAgentUpdate(@PathVariable String id,
			@RequestBody TurConverseAgent turConverseAgent) {
		return turConverseAgentRepository.save(turConverseAgent);

	}

	@Transactional
	@ApiOperation(value = "Delete a Converse Agent")
	@DeleteMapping("/{id}")
	public boolean turConverseAgentDelete(@PathVariable String id) {
		this.turConverseAgentRepository.delete(id);
		return true;
	}

	@ApiOperation(value = "Converse Agent Model")
	@GetMapping("/model")
	public TurConverseAgent turConverseAgentModel() {
		TurConverseAgent turConverseAgent = new TurConverseAgent();
		return turConverseAgent;
	}

	@ApiOperation(value = "Rebuild Chat")
	@GetMapping("/{id}/rebuild")
	public boolean turConverseAgentRebuild(@PathVariable String id) {

		TurConverseAgent turConverseAgent = turConverseAgentRepository.findById(id).get();
		Set<TurConverseIntent> turConverseIntents = turConverseIntentRepository.findByAgent(turConverseAgent);
		this.desindexAll(turConverseAgent);

		for (TurConverseIntent turConverseIntent : turConverseIntents) {
			turConverseIndex.index(turConverseIntent);
		}
		return true;
	}

	@ApiOperation(value = "Converse Chat")
	@GetMapping("/{id}/chat")
	public TurConverseAgentResponse turConverseAgentChat(@PathVariable String id,
			@RequestParam(required = false, name = "q") String q, HttpSession session) {

		TurConverseAgent turConverseAgent = turConverseAgentRepository.findById(id).get();
		TurSEInstance turSEInstance = turConverseAgent.getTurSEInstance();
		String core = turConverseAgent.getCore();

		solrClient = turSolr.getSolrClient(turSEInstance, core);

		TurConverseAgentResponse turConverseAgentResponse = new TurConverseAgentResponse();
		boolean hasParameter = false;

		if (session.getAttribute("hasParameter") != null) {
			hasParameter = ((Boolean) session.getAttribute("hasParameter")).booleanValue();
		}
		if (hasParameter) {
			try {
				int nextParameter = 0;
				if (session.getAttribute("nextParameter") != null) {
					nextParameter = (int) session.getAttribute("nextParameter");
				}

				String intent = null;
				if (session.getAttribute("intent") != null) {
					intent = (String) session.getAttribute("intent");
				}

				SolrQuery queryParameter = new SolrQuery();

				queryParameter.addFilterQuery("type:\"Parameter\"");
				queryParameter.addFilterQuery("agent:\"" + id + "\"");
				queryParameter.addFilterQuery("intent:\"" + intent + "\"");
				queryParameter.setQuery("*:*");

				QueryResponse queryResponseParameter = solrClient.query(queryParameter);
				SolrDocumentList resultsParameter = queryResponseParameter.getResults();
				if (resultsParameter.size() > nextParameter) {
					SolrDocument firstResultParameter = resultsParameter.get(nextParameter);
					if (!resultsParameter.isEmpty()) {
						List<String> prompts = (List<String>) firstResultParameter.getFieldValue("prompts");
						int rnd = new Random().nextInt(prompts.size());
						turConverseAgentResponse.setResponse(prompts.get(rnd).toString());
						turConverseAgentResponse.setIntent(intent);
					}
					session.setAttribute("nextParameter", nextParameter + 1);
				}

				if (resultsParameter.size() == nextParameter) {
					session.setAttribute("hasParameter", false);
					SolrQuery query = new SolrQuery();

					query.addFilterQuery("type:\"Intent\"");
					query.addFilterQuery("agent:\"" + id + "\"");
					query.addFilterQuery("id:\"" + intent + "\"");

					query.setQuery("*:*");
					QueryResponse queryResponse = solrClient.query(query);
					SolrDocumentList results = queryResponse.getResults();
					if (!results.isEmpty()) {
						SolrDocument firstResult = results.get(0);
						List<String> responses = (List<String>) firstResult.getFieldValue("responses");
						int rnd = new Random().nextInt(responses.size());

						List<String> contextOutputs = (List<String>) firstResult.getFieldValue("contextOutput");

						turConverseAgentResponse.setResponse(responses.get(rnd).toString());
						turConverseAgentResponse.setIntent(firstResult.getFieldValue("name").toString());

						if (contextOutputs != null && !contextOutputs.isEmpty()) {
							session.setAttribute("nextContext", contextOutputs.get(0).toString());
						}
					}
				}
			} catch (SolrServerException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			String nextContext = (String) session.getAttribute("nextContext");
			turConverseAgentResponse = this.interactionNested(id, q, turSEInstance, nextContext, session);
		}
		return turConverseAgentResponse;
	}

	@SuppressWarnings("unchecked")
	private TurConverseAgentResponse interactionNested(String agentId, String q, TurSEInstance turSEInstance,
			String nextContext, HttpSession session) {

		TurConverseAgentResponse turConverseAgentResponse = new TurConverseAgentResponse();

		try {

			SolrQuery query = new SolrQuery();

			if (nextContext != null) {
				query.addFilterQuery("type:\"Intent\"");
				query.addFilterQuery("agent:\"" + agentId + "\"");
				query.addFilterQuery("contextInput:\"" + nextContext + "\"");
			}

			query.setQuery("phrases:\"" + q + "\"");

			QueryResponse queryResponse = solrClient.query(query);
			SolrDocumentList results = queryResponse.getResults();
			if (!results.isEmpty()) {
				SolrDocument firstResult = results.get(0);

				if ((boolean) firstResult.getFieldValue("hasParameters")) {
					session.setAttribute("hasParameter", true);
					session.setAttribute("intent", firstResult.getFieldValue("id"));
					session.setAttribute("nextParameter", 1);
					SolrQuery queryParameter = new SolrQuery();

					queryParameter.addFilterQuery("type:\"Parameter\"");
					queryParameter.addFilterQuery("agent:\"" + agentId + "\"");
					queryParameter.addFilterQuery("intent:\"" + firstResult.getFieldValue("id") + "\"");
					queryParameter.setQuery("*:*");

					QueryResponse queryResponseParameter = solrClient.query(queryParameter);
					SolrDocumentList resultsParameter = queryResponseParameter.getResults();
					SolrDocument firstResultParameter = resultsParameter.get(0);
					if (!resultsParameter.isEmpty()) {
						List<String> prompts = (List<String>) firstResultParameter.getFieldValue("prompts");
						int rnd = new Random().nextInt(prompts.size());
						turConverseAgentResponse.setResponse(prompts.get(rnd).toString());
						turConverseAgentResponse.setIntent(firstResult.getFieldValue("name").toString());
					}

				} else {

					List<String> responses = (List<String>) firstResult.getFieldValue("responses");
					int rnd = new Random().nextInt(responses.size());

					List<String> contextOutputs = (List<String>) firstResult.getFieldValue("contextOutput");

					turConverseAgentResponse.setResponse(responses.get(rnd).toString());
					turConverseAgentResponse.setIntent(firstResult.getFieldValue("name").toString());

					if (contextOutputs != null && !contextOutputs.isEmpty()) {
						session.setAttribute("nextContext", contextOutputs.get(0).toString());
					}
				}
			} else {
				query = new SolrQuery();
				if (nextContext != null) {
					turConverseAgentResponse = this.interactionNested(agentId, q, turSEInstance, null, session);
				} else {
					turConverseAgentResponse.setResponse("Não sei o que dizer");
					turConverseAgentResponse.setIntent("empty");
				}
			}

		} catch (SolrServerException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return turConverseAgentResponse;
	}

	public void desindexAll(TurConverseAgent turConverseAgent) {

		TurSEInstance turSEInstance = turConverseAgent.getTurSEInstance();
		String core = turConverseAgent.getCore();

		SolrClient solrClient = turSolr.getSolrClient(turSEInstance, core);
		try {
			@SuppressWarnings("unused")
			UpdateResponse response = solrClient.deleteByQuery("agent:" + turConverseAgent.getId());
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
