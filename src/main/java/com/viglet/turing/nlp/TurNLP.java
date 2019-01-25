package com.viglet.turing.nlp;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.plugins.nlp.TurNLPImpl;
import com.viglet.turing.solr.TurSolrField;

@ComponentScan
@Component
public class TurNLP {

	@Autowired
	private TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	private TurConfigVarRepository turConfigVarRepository;
	@Autowired
	ServletContext context;
	@Autowired
	TurSolrField turSolrField;

	private Map<String, Object> nlpAttributes;

	static final Logger logger = LogManager.getLogger(TurNLP.class.getName());

	private Map<String, Object> attributes = null;

	TurNLPInstance turNLPInstance = null;
	TurNLPVendor turNLPVendor = null;
	SolrServer solrServer = null;

	public void init() {
		TurNLPInstance turNLPInstance = turNLPInstanceRepository
				.findById(Integer.parseInt(turConfigVarRepository.findById("DEFAULT_NLP").get().getValue()));
		this.init(turNLPInstance);

	}

	public void init(TurNLPInstance turNLPInstance) {

		this.turNLPInstance = turNLPInstance;
		this.turNLPVendor = turNLPInstance.getTurNLPVendor();
	}

	public void startup() {
		this.init();
	}

	public void startup(TurNLPInstance turNLPInstance, String text) {
		this.init(turNLPInstance);
		Map<String, Object> attributes = new HashMap<String, Object>();
		attributes.put("text", text);
		this.setAttributes(attributes);
	}

	public void startup(TurNLPInstance turNLPInstance, Map<String, Object> attributes) throws JSONException {
		this.init(turNLPInstance);
		this.setAttributes(attributes);

	}

	public Map<String, Object> validate() throws JSONException {
		Map<String, Object> turNLPResults = this.retrieveNLP();
		return turNLPResults;
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, Object> attributes) {
		this.attributes = attributes;
	}

	public Map<String, Object> getNlpAttributes() {
		return nlpAttributes;
	}

	public void setNlpAttributes(Map<String, Object> nlpAttributes) {
		this.nlpAttributes = nlpAttributes;
	}

	public Map<String, Object> retrieveNLP() throws JSONException {
		logger.debug("Executing retrieveNLP...");
		TurNLPImpl nlpService;

		try {
			nlpService = (TurNLPImpl) Class.forName(turNLPVendor.getPlugin()).newInstance();
			ApplicationContext applicationContext = WebApplicationContextUtils.getWebApplicationContext(context);
			applicationContext.getAutowireCapableBeanFactory().autowireBean(nlpService);
			nlpService.startup(turNLPInstance);
			this.setNlpAttributes(nlpService.retrieve(this.getAttributes()));

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (logger.isDebugEnabled() && this.getNlpAttributes() != null) {
			logger.debug("Result retrieveNLP: " + this.getNlpAttributes().toString());
		}

		return this.getNlpAttributes();
	}

}
