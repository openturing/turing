/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

package com.viglet.turing.nlp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import javax.servlet.ServletContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;
import com.viglet.turing.nlp.bean.TurNLPTrainingBean;
import com.viglet.turing.nlp.bean.TurNLPTrainingBeans;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorEntityRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.plugins.nlp.TurNLPPlugin;

@ComponentScan
@Component
public class TurNLPProcess {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	@Autowired
	private TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	private TurNLPVendorEntityRepository turNLPVendorEntityRepository;
	@Autowired
	private TurConfigVarRepository turConfigVarRepository;

	@Autowired
	private ServletContext context;

	public TurNLPInstance getDefaultNLPInstance() {
		return init().map(TurNLPRequest::getTurNLPInstance).orElse(null);
	}

	private Optional<TurNLPRequest> init() {
		Optional<TurConfigVar> turConfigVar = turConfigVarRepository.findById("DEFAULT_NLP");

		if (turConfigVar.isPresent()) {
			Optional<TurNLPInstance> turNLPInstance = turNLPInstanceRepository.findById(turConfigVar.get().getValue());
			if (turNLPInstance.isPresent()) {
				return init(turNLPInstance.get());
			}
		}
		return Optional.empty();
	}

	private Optional<TurNLPRequest> init(Map<String, Object> data, List<String> entities) {
		Optional<TurConfigVar> turConfigVar = turConfigVarRepository.findById("DEFAULT_NLP");

		if (turConfigVar.isPresent()) {
			Optional<TurNLPInstance> turNLPInstance = turNLPInstanceRepository.findById(turConfigVar.get().getValue());
			if (turNLPInstance.isPresent()) {
				return init(turNLPInstance.get(), data, entities);
			}
		}
		return Optional.empty();
	}

	private Optional<TurNLPRequest> init(TurNLPInstance turNLPInstance) {
		return init(turNLPInstance, new HashMap<>(), new ArrayList<>());
	}

	private Optional<TurNLPRequest> init(TurNLPInstance turNLPInstance, Map<String, Object> data,
			List<String> entities) {
		TurNLPRequest turNLPRequest = new TurNLPRequest();
		turNLPRequest.setTurNLPInstance(turNLPInstance);
		turNLPRequest.setData(data);
		turNLPRequest.setEntities(turNLPVendorEntityRepository
				.findByTurNLPVendorAndTurNLPEntity_internalNameIn(turNLPInstance.getTurNLPVendor(), entities));
		return Optional.ofNullable(turNLPRequest);
	}

	public TurNLPResponse processTextByDefaultNLP(String text, List<String> entities) {
		Optional<TurNLPRequest> turNLPRequest = this.init(createDataWithTextAttrib(text), entities);
		return getNLPResponse(turNLPRequest);
	}

	private TurNLPResponse getNLPResponse(Optional<TurNLPRequest> turNLPRequestOptional) {
		return turNLPRequestOptional.map(turNLPRequest -> {
			return createEntityMapFromAttributesMapToBeProcessed(turNLPRequest);
		}).orElse(new TurNLPResponse());
	}

	public TurNLPResponse processTextByNLP(TurNLPInstance turNLPInstance, String text) {
		return processTextByNLP(turNLPInstance, text, getEntitiesFromNLPVendor(turNLPInstance));
	}

	private List<String> getEntitiesFromNLPVendor(TurNLPInstance turNLPInstance) {
		List<String> entities = new ArrayList<>();
		turNLPVendorEntityRepository.findByTurNLPVendor(turNLPInstance.getTurNLPVendor())
				.forEach(entity -> entities.add(entity.getName()));
		return entities;
	}

	public TurNLPResponse processTextByNLP(TurNLPInstance turNLPInstance, String text, List<String> entities) {
		Optional<TurNLPRequest> turNLPRequest = this.init(turNLPInstance, createDataWithTextAttrib(text), entities);
		return getNLPResponse(turNLPRequest);
	}

	private Map<String, Object> createDataWithTextAttrib(String text) {
		Map<String, Object> attribs = new HashMap<>();
		attribs.put("text", text);
		return attribs;
	}

	public TurNLPResponse processAttribsByNLP(TurNLPInstance turNLPInstance, Map<String, Object> attributes) {
		return processAttribsByNLP(turNLPInstance, attributes, getEntitiesFromNLPVendor(turNLPInstance));

	}

	public TurNLPResponse processAttribsByNLP(TurNLPInstance turNLPInstance, Map<String, Object> attributes,
			List<String> entities) {
		Optional<TurNLPRequest> turNLPRequest = this.init(turNLPInstance, attributes, entities);
		return getNLPResponse(turNLPRequest);

	}

	private TurNLPResponse createEntityMapFromAttributesMapToBeProcessed(TurNLPRequest turNLPRequest) {
		logger.debug("Executing retrieveNLP...");
		TurNLPPlugin nlpService;
		TurNLPResponse turNLPResponse = new TurNLPResponse();
		try {
			nlpService = (TurNLPPlugin) Class.forName(turNLPRequest.getTurNLPInstance().getTurNLPVendor().getPlugin())
					.getDeclaredConstructor().newInstance();
			ApplicationContext applicationContext = WebApplicationContextUtils.getWebApplicationContext(context);
			if (applicationContext != null) {
				applicationContext.getAutowireCapableBeanFactory().autowireBean(nlpService);
				turNLPResponse.setEntityMapWithProcessedValues(nlpService.processAttributesToEntityMap(turNLPRequest));
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

		if (logger.isDebugEnabled() && turNLPResponse.getEntityMapWithProcessedValues() != null) {
			logger.debug("Result retrieveNLP: {}", turNLPResponse.getEntityMapWithProcessedValues());
		}

		return turNLPResponse;

	}

	public void fixEntityMapBasedInManualEntity(Map<String, List<String>> entityMapWithProcessedValues) {
		logger.debug("Executing processNLPTerms");
		Map<String, List<String>> processedAttributes = new HashMap<>();
		File userDir = new File(System.getProperty("user.dir"));
		File trainingFile = new File(userDir.getAbsolutePath().concat("/store/nlp/train/train.json"));
		if (trainingFile.exists()) {
			try (BufferedReader rd = new BufferedReader(new FileReader(trainingFile))) {
				TurNLPTrainingBeans turNLPTrainingBeans = new ObjectMapper().readValue(CharStreams.toString(rd),
						TurNLPTrainingBeans.class);
				Map<String, TurNLPTrainingBean> terms = termMapOfTraining(turNLPTrainingBeans);
				logTermsOfTraining(turNLPTrainingBeans);
				remapEntityMap(entityMapWithProcessedValues, processedAttributes, terms);
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
			entityMapWithProcessedValues = processedAttributes;
		}
	}

	private void remapEntityMap(Map<String, List<String>> entityMapWithProcessedValues,
			Map<String, List<String>> processedAttributes, Map<String, TurNLPTrainingBean> terms) {
		if (entityMapWithProcessedValues != null) {
			for (Entry<String, List<String>> attribute : entityMapWithProcessedValues.entrySet()) {
				processAttributeOfEntityMap(processedAttributes, terms, attribute);
			}
		}
	}

	private void processAttributeOfEntityMap(Map<String, List<String>> processedAttributes,
			Map<String, TurNLPTrainingBean> terms, Entry<String, List<String>> attribute) {
		if (attribute.getValue() != null) {
			logger.debug("attribute Value: {}", attribute.getValue());

			if (!processedAttributes.containsKey(attribute.getKey()))
				processedAttributes.put(attribute.getKey(), new ArrayList<>());
			for (Object attributeValueItem : attribute.getValue()) {
				changeAttribute(processedAttributes, terms, attribute, attributeValueItem);
			}
		}
	}

	private void changeAttribute(Map<String, List<String>> processedAttributes, Map<String, TurNLPTrainingBean> terms,
			Entry<String, List<String>> attribute, Object attributeValueItem) {
		if (terms.containsKey(attributeValueItem.toString().toLowerCase())) {
			useTermOfManualEntityAndRemap(processedAttributes, terms, attribute, attributeValueItem);
		} else {
			processedAttributes.get(attribute.getKey()).add(attributeValueItem.toString());
		}
	}

	private void useTermOfManualEntityAndRemap(Map<String, List<String>> processedAttributes,
			Map<String, TurNLPTrainingBean> terms, Entry<String, List<String>> attribute, Object attributeValueItem) {
		TurNLPTrainingBean term = terms.get(attributeValueItem.toString().toLowerCase());
		if (!term.isIgnore()) {
			if (term.getNer() != null) {
				remapToNewNer(processedAttributes, attributeValueItem, term);
			} else {
				changeValueBasedOnConvertTo(processedAttributes, attribute, attributeValueItem, term);
			}
		}
	}

	private void changeValueBasedOnConvertTo(Map<String, List<String>> processedAttributes,
			Entry<String, List<String>> attribute, Object attributeValueItem, TurNLPTrainingBean term) {
		if (term.getConvertTo() != null)
			processedAttributes.get(attribute.getKey()).add(term.getConvertTo());
		else
			processedAttributes.get(attribute.getKey()).add(attributeValueItem.toString());
	}

	private void remapToNewNer(Map<String, List<String>> processedAttributes, Object attributeValueItem,
			TurNLPTrainingBean term) {
		if (!processedAttributes.containsKey(term.getNer()))
			processedAttributes.put(term.getNer(), new ArrayList<>());
		if (term.getConvertTo() != null)
			processedAttributes.get(term.getNer()).add(term.getConvertTo());
		else
			processedAttributes.get(term.getNer()).add(attributeValueItem.toString());
	}

	private Map<String, TurNLPTrainingBean> termMapOfTraining(TurNLPTrainingBeans turNLPTrainingBeans) {
		Map<String, TurNLPTrainingBean> terms = new HashMap<>();
		for (TurNLPTrainingBean turNLPTrainingBeanItem : turNLPTrainingBeans.getTerms()) {
			terms.put(turNLPTrainingBeanItem.getTerm().toLowerCase(), turNLPTrainingBeanItem);
		}

		return terms;
	}

	private void logTermsOfTraining(TurNLPTrainingBeans turNLPTrainingBeans) {
		if (logger.isDebugEnabled()) {
			for (TurNLPTrainingBean turNLPTrainingBeanItem : turNLPTrainingBeans.getTerms()) {
				logger.debug(turNLPTrainingBeanItem);
			}
		}
	}
}
