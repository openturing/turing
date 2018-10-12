package com.viglet.turing.api.sn.queue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import com.viglet.turing.api.sn.job.TurSNJob;
import com.viglet.turing.api.sn.job.TurSNJobAction;
import com.viglet.turing.api.sn.job.TurSNJobItem;
import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.thesaurus.TurThesaurusProcessor;

@Component
public class TurSNProcessQueue {
	static final Logger logger = LogManager.getLogger(TurSNProcessQueue.class.getName());
	@Autowired
	TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	TurConfigVarRepository turConfigVarRepository;
	@Autowired
	TurSolr turSolr;
	@Autowired
	TurSNSiteRepository turSNSiteRepository;
	@Autowired
	TurNLP turNLP;
	@Autowired
	TurThesaurusProcessor turThesaurusProcessor;
	@Autowired
	TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

	public static final String INDEXING_QUEUE = "indexing.queue";

	@JmsListener(destination = INDEXING_QUEUE)
	public void receiveIndexingQueue(TurSNJob turSNJob) {

		TurSNSite turSNSite = this.turSNSiteRepository.findById(turSNJob.getSiteId()).get();
		for (TurSNJobItem turSNJobItem : turSNJob.getTurSNJobItems()) {
			logger.debug("receiveQueue TurSNJobItem: " + turSNJobItem.toString());
			if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.CREATE)) {
				this.indexing(turSNJobItem, turSNSite);

			} else if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.DELETE)) {
				this.desindexing(turSNJobItem, turSNSite);
			}
		}
	}

	public void desindexing(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
		logger.debug("Deindexing");

		turSolr.init(turSNSite);
		if (turSNJobItem.getAttributes().containsKey("id")) {
			turSolr.desindexing((String) turSNJobItem.getAttributes().get("id"));
		} else if (turSNJobItem.getAttributes().containsKey("type")) {
			turSolr.desindexingByType((String) turSNJobItem.getAttributes().get("type"));
		}
	}

	public void indexing(TurSNJobItem turSNJobItem, TurSNSite turSNSite) {
		logger.debug("Indexing");
		Map<String, Object> consolidateResults = new HashMap<String, Object>();

		// SE
		for (Entry<String, Object> attribute : turSNJobItem.getAttributes().entrySet()) {
			logger.debug("SE Consolidate Value: " + attribute.getValue());
			logger.debug("SE Consolidate Class: " + attribute.getValue().getClass().getName());
			consolidateResults.put(attribute.getKey(), attribute.getValue());
		}

		// NLP
		boolean nlp = true;
		if (turSNSite.getTurNLPInstance() != null && turSNSite.getTurNLPInstance().getId() >= 1) {
			logger.debug("It is using NLP to process attributes");
			nlp = true;
		} else {
			logger.debug("It is not using NLP to process attributes");
			nlp = false;
		}

		if (nlp) {
			List<TurSNSiteFieldExt> turSNSiteFieldsExt = turSNSiteFieldExtRepository
					.findByTurSNSiteAndNlpAndEnabled(turSNSite, 1, 1);

			// Convert List to HashMap
			Map<String, TurSNSiteFieldExt> turSNSiteFieldsExtMap = new HashMap<String, TurSNSiteFieldExt>();
			for (TurSNSiteFieldExt turSNSiteFieldExt : turSNSiteFieldsExt) {
				turSNSiteFieldsExtMap.put(turSNSiteFieldExt.getName().toLowerCase(), turSNSiteFieldExt);
			}

			// Select only fields that is checked as NLP. These attributes will be processed
			// by NLP
			HashMap<String, Object> nlpAttributes = new HashMap<String, Object>();
			for (Entry<String, Object> attribute : turSNJobItem.getAttributes().entrySet()) {
				if (turSNSiteFieldsExtMap.containsKey(attribute.getKey().toLowerCase())) {
					nlpAttributes.put(attribute.getKey(), attribute.getValue());
				}
			}

			turNLP.startup(turSNSite.getTurNLPInstance(), nlpAttributes);
			Map<String, Object> nlpResults = turNLP.retrieveNLP();
			Map<String, Object> nlpResultsPreffix = new HashMap<String, Object>();

			// Add prefix to attribute name
			for (Entry<String, Object> nlpResult : nlpResults.entrySet()) {
				nlpResultsPreffix.put("turing_entity_" + nlpResult.getKey(), nlpResult.getValue());
			}

			// Copy NLP attributes to consolidateResults
			for (Entry<String, Object> nlpResultPreffix : nlpResultsPreffix.entrySet()) {
				consolidateResults.put(nlpResultPreffix.getKey(), nlpResultPreffix.getValue());
			}
		}

		// Thesaurus
		boolean thesaurus = false;
		if (turSNSite.getThesaurus() < 1) {
			logger.debug("It is not using Thesaurus to process attributes");
			thesaurus = false;
		} else {
			logger.debug("It is using Thesaurus to process attributes");
			thesaurus = true;
		}
		if (thesaurus) {
			turThesaurusProcessor.startup();
			Map<String, Object> thesaurusResults = turThesaurusProcessor.detectTerms(turSNJobItem.getAttributes());

			for (Entry<String, Object> thesaurusResult : thesaurusResults.entrySet()) {
				consolidateResults.put(thesaurusResult.getKey(), thesaurusResult.getValue());
			}
		}

		// Remove Duplicate Terms
		Map<String, Object> attributesWithUniqueTerms = this.removeDuplicateTerms(consolidateResults);

		// SE
		turSolr.init(turSNSite, attributesWithUniqueTerms);
		turSolr.indexing();
	}

	public Map<String, Object> removeDuplicateTerms(Map<String, Object> attributes) {
		Map<String, Object> attributesWithUniqueTerms = new HashMap<String, Object>();
		if (attributes != null) {
			for (Entry<String, Object> attribute : attributes.entrySet()) {
				if (attribute.getValue() != null) {

					logger.debug("removeDuplicateTerms: attribute Value: " + attribute.getValue().toString());
					logger.debug("removeDuplicateTerms: attribute Class: " + attribute.getValue().getClass().getName());
					if (attribute.getValue() instanceof ArrayList) {

						ArrayList<?> nlpAttributeArray = (ArrayList<?>) attribute.getValue();
						if (nlpAttributeArray.size() > 0) {
							List<String> list = new ArrayList<String>();
							for (Object nlpAttributeItem : nlpAttributeArray) {
								list.add((String) nlpAttributeItem);
							}
							Set<String> termsUnique = new HashSet<String>(list);
							List<Object> arrayValue = new ArrayList<Object>();
							arrayValue.addAll(termsUnique);
							attributesWithUniqueTerms.put(attribute.getKey(), arrayValue);
							for (Object term : termsUnique) {
								logger.debug("removeDuplicateTerms: attributesWithUniqueTerms Array Value: "
										+ (String) term);
							}
						}
					} else {

						attributesWithUniqueTerms.put(attribute.getKey(), attribute.getValue());
					}
				}
			}
			logger.debug("removeDuplicateTerms: attributesWithUniqueTerms: " + attributesWithUniqueTerms.toString());

		}
		return attributesWithUniqueTerms;
	}
}
