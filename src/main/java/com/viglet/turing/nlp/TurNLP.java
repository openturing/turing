package com.viglet.turing.nlp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.viglet.turing.nlp.entity.TurNLPEntityProcessor;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.plugins.nlp.TurNLPImpl;

@ComponentScan
@Component
public class TurNLP {

	@Autowired
	private TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	private TurConfigVarRepository turConfigVarRepository;
	@Autowired
	TurNLPEntityProcessor turNLPEntityProcessor;
	@Autowired
	ServletContext context; 
	
	static final Logger logger = LogManager.getLogger(TurNLP.class.getName());
	
	private String currText = null;
	private JSONObject jsonAttributes = null;

	TurNLPInstance turNLPInstance = null;
	TurNLPVendor turNLPVendor = null;
	TurNLPResults turNLPResults = null;
	SolrServer solrServer = null;

	

	public void init() {
		TurNLPInstance turNLPInstance = turNLPInstanceRepository
				.findById(Integer.parseInt(turConfigVarRepository.findById("DEFAULT_NLP").getValue()));
		this.init(turNLPInstance);
	}

	public void init(TurNLPInstance turNLPInstance) {
		
		this.turNLPInstance = turNLPInstance;
		this.turNLPVendor = turNLPInstance.getTurNLPVendor();
		this.turNLPResults = new TurNLPResults();
	}

	public void startup() {
		this.init();
		this.setCurrText(null);
	}

	public void startup(TurNLPInstance turNLPInstance, String text) {
		this.init(turNLPInstance);
		this.setCurrText(text);
	}

	public void startup(TurNLPInstance turNLPInstance, JSONObject jsonAttributes) throws JSONException {
		this.init(turNLPInstance);

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

	public String validate() throws JSONException {
		TurNLPResults turNLPResults = this.retrieveNLP();
		return turNLPResults.getJsonResult().toString();
	}

	public JSONObject getJsonAttributes() {
		return jsonAttributes;
	}

	public void setJsonAttributes(JSONObject jsonAttributes) {
		this.jsonAttributes = jsonAttributes;
	}

	public String getCurrText() {
		return currText;
	}

	public void setCurrText(String currText) {
		this.currText = currText;
	}

	public TurNLPResults retrieveNLP() throws JSONException {
		logger.debug("Executing retrieveNLP...");
		TurNLPImpl nlpService;

		try {
			nlpService = (TurNLPImpl) Class.forName(turNLPVendor.getPlugin()).newInstance();
			ApplicationContext applicationContext = WebApplicationContextUtils.getWebApplicationContext(context);
			applicationContext.getAutowireCapableBeanFactory().autowireBean(nlpService);
			nlpService.startup(turNLPInstance);
			turNLPResults = nlpService.retrieve(this.getCurrText());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		this.turNLPResults.setJsonAttributes(this.getJsonAttributes());

		// TurNLP Entities
		turNLPEntityProcessor.startup();
		LinkedHashMap<String, List<String>> entityResults = turNLPEntityProcessor.detectTerms(currText);

		for (String entity : entityResults.keySet()) {
			JSONArray jsonEntity = new JSONArray();
			logger.debug("entity from retrieveNLP: " + entity);
			List<String> lstTerm = entityResults.get(entity);
			for (String resultTerm : lstTerm) {
				jsonEntity.put(resultTerm);
			}
			try {
				this.turNLPResults.getJsonResult().put(entity, jsonEntity);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		this.removeDuplicateTerms();

		return turNLPResults;
	}

	public void removeDuplicateTerms() throws JSONException {
		JSONObject jsonNLP = this.turNLPResults.getJsonResult();
		for (TurNLPInstanceEntity turNLPEntity : turNLPResults.getTurNLPInstanceEntities()) {

			if (jsonNLP.has(turNLPEntity.getTurNLPEntity().getCollectionName())) {
				JSONArray jsonEntity = jsonNLP.getJSONArray(turNLPEntity.getTurNLPEntity().getCollectionName());

				if (jsonEntity.length() > 0) {
					List<String> list = new ArrayList<String>();
					for (int i = 0; i < jsonEntity.length(); i++) {
						list.add(jsonEntity.getString(i));
					}
					Set<String> termsUnique = new HashSet<String>(list);

					jsonNLP.remove(turNLPEntity.getTurNLPEntity().getCollectionName());
					jsonNLP.put(turNLPEntity.getTurNLPEntity().getCollectionName(),
							new JSONArray(termsUnique.toArray()));
				}
			}
		}

	}
}
