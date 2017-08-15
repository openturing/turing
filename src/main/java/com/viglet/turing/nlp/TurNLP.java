package com.viglet.turing.nlp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.json.JSONArray;
import org.json.JSONObject;

import com.viglet.turing.nlp.entity.TurNLPEntityProcessor;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.service.nlp.TurNLPInstanceService;
import com.viglet.turing.plugins.nlp.TurNLPImpl;

public class TurNLP {
	static final Logger logger = LogManager.getLogger(TurNLP.class.getName());
	
	private int currNLP = 0;
	private String currText = null;
	private JSONObject jsonAttributes = null;

	TurNLPInstance turNLPInstance = null;
	TurNLPVendor turNLPVendor = null;
	TurNLPResults turNLPResults = null;
	SolrServer solrServer = null;

	TurNLPInstanceService turNLPInstanceService = new TurNLPInstanceService();
	public void init(int nlp) {

		this.setCurrNLP(nlp);

		this.turNLPInstance = turNLPInstanceService.get(this.getCurrNLP());
		this.turNLPVendor = turNLPInstance.getTurNLPVendor();
		this.turNLPResults = new TurNLPResults();
	}

	public TurNLP() {
		super();
		
		init(turNLPInstanceService.getNLPDefault().getId());
		this.setCurrText(null);

	}

	public TurNLP(int nlp, String text) {
		super();
		init(nlp);
		this.setCurrText(text);

	}

	public TurNLP(int nlp, JSONObject jsonAttributes) {
		super();
		init(nlp);

		StringBuffer sbText = new StringBuffer();

		Iterator<?> keys = jsonAttributes.keys();
		while (keys.hasNext()) {

			String key = (String) keys.next();
			if (key.equals("text") || key.equals("abstract") || key.equals("title")) {
				sbText.append((String) jsonAttributes.get(key));
			}

		}
		this.setCurrText(sbText.toString());
		this.setJsonAttributes(jsonAttributes);

	}

	public JSONObject getJsonAttributes() {
		return jsonAttributes;
	}

	public void setJsonAttributes(JSONObject jsonAttributes) {
		this.jsonAttributes = jsonAttributes;
	}

	public int getCurrNLP() {
		return currNLP;
	}

	public void setCurrNLP(int currNLP) {
		this.currNLP = currNLP;
	}

	public String getCurrText() {
		return currText;
	}

	public void setCurrText(String currText) {
		this.currText = currText;
	}

	public TurNLPResults retrieveNLP() {
		logger.debug("Executing retrieveNLP...");
		TurNLPImpl nlpService;

		TurNLPEntityProcessor turNLPEntityProcessor = new TurNLPEntityProcessor();

		try {
			nlpService = (TurNLPImpl) Class.forName(turNLPVendor.getPlugin())
					.getConstructor(new Class[] { TurNLPInstance.class }).newInstance(new Object[] { turNLPInstance });
			turNLPResults = nlpService.retrieve(this.getCurrText());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.turNLPResults.setJsonAttributes(this.getJsonAttributes());
		
		// TurNLP Entities
		LinkedHashMap<String, List<String>> entityResults = turNLPEntityProcessor.detectTerms(currText);
		
		
		for (String entity : entityResults.keySet()) {
			JSONArray jsonEntity = new JSONArray();
			logger.debug("entity from retrieveNLP: " + entity);
			List<String> lstTerm = entityResults.get(entity);
			for (String resultTerm : lstTerm) {		
				jsonEntity.put(resultTerm);
			}
			this.turNLPResults.getJsonResult().put(entity, jsonEntity);			
		}
		
		
		this.removeDuplicateTerms();

		return turNLPResults;
	}

	public void removeDuplicateTerms() {
		JSONObject jsonNLP = this.turNLPResults.getJsonResult();
		for (TurNLPInstanceEntity turNLPEntity : turNLPResults.getTurNLPInstanceEntities()) {

			if (jsonNLP.has(turNLPEntity.getTurNLPEntity().getCollectionName())) {
				JSONArray jsonEntity = jsonNLP.getJSONArray(turNLPEntity.getTurNLPEntity().getCollectionName());

				if (jsonEntity.length() > 0) {
					List<String> list = new ArrayList<String>();
					for(int i = 0; i < jsonEntity.length(); i++){
					    list.add(jsonEntity.getString(i));
					}				
					Set<String> termsUnique = new HashSet<String>(list);
					
					jsonNLP.remove(turNLPEntity.getTurNLPEntity().getCollectionName());
					jsonNLP.put(turNLPEntity.getTurNLPEntity().getCollectionName(), new JSONArray(termsUnique.toArray()));
				}
			}
		}

	}
}
