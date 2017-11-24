package com.viglet.turing.api.sn;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.plugins.corenlp.TurCoreNLPConnector;
import com.viglet.turing.solr.TurSolr;
import com.viglet.turing.thesaurus.TurThesaurusProcessor;

@Component
public class TurSNProcessQueue {
	static final Logger logger = LogManager.getLogger(TurSNJob.class.getName());
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

	@JmsListener(destination = "sample.queue")
	public void receiveQueue(TurSNJob turSNJob) {

		JSONArray jsonRows = new JSONArray(turSNJob.getJson());
		TurSNSite turSNSite = this.turSNSiteRepository.findById(Integer.parseInt(turSNJob.getSiteId()));
		try {
			for (int i = 0; i < jsonRows.length(); i++) {
				JSONObject jsonRow = jsonRows.getJSONObject(i);
				logger.debug("receiveQueue JsonObject: " + jsonRow.toString());
				ObjectMapper mapper = new ObjectMapper();
				TypeFactory typeFactory = mapper.getTypeFactory();
				MapType mapType = typeFactory.constructMapType(HashMap.class, String.class, Object.class);
				HashMap<String, Object> attributes = mapper.readValue(new StringReader(jsonRow.toString()), mapType);

				Map<String, Object> consolidateResults = new HashMap<String, Object>();

				// SE
				for (Entry<String, Object> attribute : attributes.entrySet()) {
					logger.debug("SE Consolidate Value: " + attribute.getValue());
					logger.debug("SE Consolidate Class: " + attribute.getValue().getClass().getName());
					consolidateResults.put(attribute.getKey(), attribute.getValue());
				}

				// NLP
				boolean nlp = true;

				if (nlp) {
					turNLP.startup(turSNSite.getTurNLPInstance(), attributes);
					Map<String, Object> nlpResults = turNLP.retrieveNLP();
					Map<String, Object> nlpResultsPreffix = new HashMap<String, Object>();

					for (Entry<String, Object> nlpResult : nlpResults.entrySet()) {
						nlpResultsPreffix.put("turing_entity_" + nlpResult.getKey(), nlpResult.getValue());
					}

					for (Entry<String, Object> nlpResultPreffix : nlpResultsPreffix.entrySet()) {
						consolidateResults.put(nlpResultPreffix.getKey(), nlpResultPreffix.getValue());
					}
				}

				// Thesaurus
				boolean thesaurus = false;
				if (thesaurus) {
					turThesaurusProcessor.startup();
					Map<String, Object> thesaurusResults = turThesaurusProcessor.detectTerms(attributes);

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
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Received job");
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
