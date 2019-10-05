package com.viglet.turing.converse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseContext;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseParameter;
import com.viglet.turing.persistence.model.converse.intent.TurConversePhrase;
import com.viglet.turing.persistence.model.converse.intent.TurConversePrompt;
import com.viglet.turing.persistence.model.converse.intent.TurConverseResponse;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseContextRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseParameterRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePhraseRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePromptRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseResponseRepository;
import com.viglet.turing.solr.TurSolr;

@Component
public class TurConverseSE {

	@Autowired
	private TurSolr turSolr;
	@Autowired
	private TurConversePhraseRepository turConversePhraseRepository;
	@Autowired
	private TurConverseResponseRepository turConverseResponseRepository;
	@Autowired
	private TurConverseContextRepository turConverseContextRepository;
	@Autowired
	private TurConverseParameterRepository turConverseParameterRepository;
	@Autowired
	private TurConversePromptRepository turConversePromptRepository;
	
	private SolrClient getSolrClient(TurConverseAgent turConverseAgent) {
		TurSEInstance turSEInstance = turConverseAgent.getTurSEInstance();
		String core = turConverseAgent.getCore();

		SolrClient solrClient = turSolr.getSolrClient(turSEInstance, core);
		return solrClient;
	}
	

	SolrDocumentList solrAskPhrase(TurConverseAgent turConverseAgent, String q, String nextContext)
			throws SolrServerException, IOException {
		SolrClient solrClient = this.getSolrClient(turConverseAgent);
		SolrQuery query = new SolrQuery();
		
		if (nextContext != null) {
			
			query.addFilterQuery("contextInput:\"" + nextContext + "\"");
		} else {
			query.addFilterQuery("-contextInput:[\"\" TO *]");
		}
		query.addFilterQuery("type:\"Intent\"");
		query.addFilterQuery("agent:\"" + turConverseAgent.getId() + "\"");
		query.setQuery("phrases:\"" + q + "\"");

		QueryResponse queryResponse = solrClient.query(query);
		
		
		return queryResponse.getResults();
	}
	
	SolrDocumentList solrGetActionAndParameters(TurConverseAgent turConverseAgent, String intent)
			throws SolrServerException, IOException {
		SolrClient solrClient = this.getSolrClient(turConverseAgent);
		SolrQuery queryParameter = new SolrQuery();

		queryParameter.addFilterQuery("type:\"Parameter\"");
		queryParameter.addFilterQuery("agent:\"" + turConverseAgent.getId() + "\"");
		queryParameter.addFilterQuery("intent:\"" + intent + "\"");
		queryParameter.setQuery("*:*");

		QueryResponse queryResponseParameter = solrClient.query(queryParameter);
		SolrDocumentList resultsParameter = queryResponseParameter.getResults();
		return resultsParameter;
	}

	
	
	SolrDocumentList solrGetIntent(TurConverseAgent turConverseAgent, String intent) throws SolrServerException, IOException {
		SolrClient solrClient = this.getSolrClient(turConverseAgent);
		SolrQuery query = new SolrQuery();

		query.addFilterQuery("type:\"Intent\"");
		query.addFilterQuery("agent:\"" + turConverseAgent.getId() + "\"");
		query.addFilterQuery("id:\"" + intent + "\"");

		query.setQuery("*:*");
		QueryResponse queryResponse = solrClient.query(query);
		SolrDocumentList results = queryResponse.getResults();
		return results;
	}
	
	public void desindexAll(TurConverseAgent turConverseAgent) {

		SolrClient solrClient = this.getSolrClient(turConverseAgent);
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

	public void index(TurConverseIntent turConverseIntent) {
		turConverseIntent.setContextInputs(
				turConverseContextRepository.findByIntentInputs(new HashSet<>(Arrays.asList(turConverseIntent))));
		turConverseIntent.setContextOutputs(
				turConverseContextRepository.findByIntentOutputs(new HashSet<>(Arrays.asList(turConverseIntent))));
		turConverseIntent.setPhrases(turConversePhraseRepository.findByIntent(turConverseIntent));
		turConverseIntent.setResponses(turConverseResponseRepository.findByIntent(turConverseIntent));
		turConverseIntent.setParameters(turConverseParameterRepository.findByIntent(turConverseIntent));

		for (TurConverseParameter parameter : turConverseIntent.getParameters()) {
			parameter.setPrompts(turConversePromptRepository.findByParameter(parameter));
		}

		TurSEInstance turSEInstance = turConverseIntent.getAgent().getTurSEInstance();
		String core = turConverseIntent.getAgent().getCore();

		SolrClient solrClient = turSolr.getSolrClient(turSEInstance, core);

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

		document.addField("hasParameters", turConverseIntent.getParameters().size() > 0 ? true : false);

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

		// Parameters

		for (TurConverseParameter parameter : turConverseIntent.getParameters()) {
			document = new SolrInputDocument();
			document.addField("id", parameter.getId());
			document.addField("agent", turConverseIntent.getAgent().getId());
			document.addField("intent", turConverseIntent.getId());
			document.addField("type", "Parameter");
			document.addField("action", turConverseIntent.getActionName());
			document.addField("name", parameter.getName());
			document.addField("position", parameter.getPosition());

			List<String> promptList = new ArrayList<>();
			for (TurConversePrompt prompt : parameter.getPrompts()) {
				promptList.add(prompt.getText());
			}
			document.addField("prompts", promptList);

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

	public void desindex(TurConverseIntent turConverseIntent) {

		TurSEInstance turSEInstance = turConverseIntent.getAgent().getTurSEInstance();
		String core = turConverseIntent.getAgent().getCore();

		SolrClient solrClient = turSolr.getSolrClient(turSEInstance, core);

		try {
			@SuppressWarnings("unused")
			UpdateResponse response = solrClient.deleteByQuery("id:" + turConverseIntent.getId());
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
