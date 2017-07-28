package com.viglet.turing.nlp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.json.JSONArray;
import org.json.JSONObject;

import com.viglet.turing.entity.VigEntityProcessor;
import com.viglet.turing.persistence.model.TurNLPVendor;
import com.viglet.turing.persistence.model.VigService;
import com.viglet.turing.persistence.service.TurNLPInstanceService;
import com.viglet.turing.persistence.service.TurNLPVendorService;
import com.viglet.turing.persistence.model.TurNLPInstance;
import com.viglet.turing.persistence.model.TurNLPInstanceEntity;
import com.viglet.turing.plugins.nlp.NLPImpl;
import com.viglet.turing.service.VigServiceUtil;

public class VigNLP {
	static final Logger logger = LogManager.getLogger(VigNLP.class.getName());
	
	private int currNLP = 0;
	private String currText = null;
	private JSONObject jsonAttributes = null;
	VigService vigServiceSE = null;

	TurNLPInstance turNLPInstance = null;
	TurNLPVendor turNLPVendor = null;
	VigNLPResults vigNLPResults = null;
	SolrServer solrServer = null;
	EntityManager em = null;

	TurNLPInstanceService turNLPInstanceService = new TurNLPInstanceService();
	public void init(int nlp) {

		this.setCurrNLP(nlp);

		this.turNLPInstance = turNLPInstanceService.get(this.getCurrNLP());
		this.turNLPVendor = turNLPInstance.getTurNLPVendor();
		this.vigNLPResults = new VigNLPResults();
	}

	public VigNLP() {
		super();
		
		init(turNLPInstanceService.getNLPDefault().getId());
		this.setCurrText(null);

	}

	public VigNLP(int nlp, String text) {
		super();
		init(nlp);
		this.setCurrText(text);

	}

	public VigNLP(int nlp, JSONObject jsonAttributes) {
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

	public VigNLPResults retrieveNLP() {
		logger.debug("Executing retrieveNLP...");
		NLPImpl nlpService;

		VigEntityProcessor vigEntityProcessor = new VigEntityProcessor();

		try {
			nlpService = (NLPImpl) Class.forName(turNLPVendor.getPlugin())
					.getConstructor(new Class[] { TurNLPInstance.class }).newInstance(new Object[] { turNLPInstance });
			vigNLPResults = nlpService.retrieve(this.getCurrText());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.vigNLPResults.setJsonAttributes(this.getJsonAttributes());
		
		// VigNLP Entities
		LinkedHashMap<String, List<String>> entityResults = vigEntityProcessor.detectTerms(currText);
		
		
		for (String entity : entityResults.keySet()) {
			JSONArray jsonEntity = new JSONArray();
			logger.debug("entity from retrieveNLP: " + entity);
			List<String> lstTerm = entityResults.get(entity);
			for (String resultTerm : lstTerm) {		
				jsonEntity.put(resultTerm);
			}
			this.vigNLPResults.getJsonResult().put(entity, jsonEntity);			
		}
		
		
		this.removeDuplicateTerms();

		return vigNLPResults;
	}

	public void removeDuplicateTerms() {
		JSONObject jsonNLP = this.vigNLPResults.getJsonResult();
		for (TurNLPInstanceEntity turNLPEntity : vigNLPResults.getTurNLPInstanceEntities()) {

			if (jsonNLP.has(turNLPEntity.getTurEntity().getCollectionName())) {
				JSONArray jsonEntity = jsonNLP.getJSONArray(turNLPEntity.getTurEntity().getCollectionName());

				if (jsonEntity.length() > 0) {
					List<String> list = new ArrayList<String>();
					for(int i = 0; i < jsonEntity.length(); i++){
					    list.add(jsonEntity.getString(i));
					}				
					Set<String> termsUnique = new HashSet<String>(list);
					
					jsonNLP.remove(turNLPEntity.getTurEntity().getCollectionName());
					jsonNLP.put(turNLPEntity.getTurEntity().getCollectionName(), new JSONArray(termsUnique.toArray()));
				}
			}
		}

	}
}
