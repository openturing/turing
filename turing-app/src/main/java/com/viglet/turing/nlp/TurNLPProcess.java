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
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceEntityRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.plugins.nlp.TurNLPPlugin;

@ComponentScan
@Component
public class TurNLPProcess {
	private static final Logger logger = LogManager.getLogger(TurNLPProcess.class);
	@Autowired
	private TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	private TurConfigVarRepository turConfigVarRepository;
	@Autowired
	private ServletContext context;
	@Autowired
	private TurNLPInstanceEntityRepository turNLPInstanceEntityRepository;

	public TurNLPInstance getDefaultNLPInstance() {
		return init().map(TurNLP::getTurNLPInstance).orElse(null);
	}

	private Optional<TurNLP> init() {
		Optional<TurConfigVar> turConfigVar = turConfigVarRepository.findById("DEFAULT_NLP");
		
		if (turConfigVar.isPresent()) {
			Optional<TurNLPInstance> turNLPInstance = turNLPInstanceRepository.findById(turConfigVar.get().getValue());
			if (turNLPInstance.isPresent()) {
				return init(turNLPInstance.get());
			}
		}
		return Optional.empty();
	}

	private Optional<TurNLP> init(TurNLPInstance turNLPInstance) {
		TurNLP turNLP = new TurNLP();
		turNLP.setTurNLPInstance(turNLPInstance);
		turNLP.setTurNLPVendor(turNLPInstance.getTurNLPVendor());
		turNLP.setNlpInstanceEntities(turNLPInstanceEntityRepository.findByTurNLPInstanceAndEnabled(turNLPInstance, 1));
		return Optional.ofNullable(turNLP);
	}

	public Optional<TurNLP> processTextByDefaultNLP(String text) {
		Optional<TurNLP> turNLP = this.init();

		Map<String, Object> attribs = new HashMap<>();
		attribs.put("text", text);
		setNLPAttribs(turNLP, attribs);
		return turNLP;
	}

	private void setNLPAttribs(Optional<TurNLP> turNLPOptional, Map<String, Object> attribs) {
		turNLPOptional.ifPresent(turNLP -> {
			turNLP.setAttributeMapToBeProcessed(attribs);
			createEntityMapFromAttributesMapToBeProcessed(turNLP);
		});
	}

	public Optional<TurNLP> processTextByNLP(TurNLPInstance turNLPInstance, String text) {
		Optional<TurNLP> turNLP = this.init(turNLPInstance);
		Map<String, Object> attribs = new HashMap<>();
		attribs.put("text", text);
		setNLPAttribs(turNLP, attribs);
		return turNLP;
	}

	public Optional<TurNLP> processAttribsByNLP(TurNLPInstance turNLPInstance, Map<String, Object> attributes) {
		Optional<TurNLP> turNLP = this.init(turNLPInstance);
		setNLPAttribs(turNLP, attributes);
		return turNLP;

	}

	private void createEntityMapFromAttributesMapToBeProcessed(TurNLP turNLP) {
		logger.debug("Executing retrieveNLP...");
		TurNLPPlugin nlpService;

		try {
			nlpService = (TurNLPPlugin) Class.forName(turNLP.getTurNLPVendor().getPlugin()).getDeclaredConstructor()
					.newInstance();
			ApplicationContext applicationContext = WebApplicationContextUtils.getWebApplicationContext(context);
			if (applicationContext != null) {
				applicationContext.getAutowireCapableBeanFactory().autowireBean(nlpService);
				turNLP.setEntityMapWithProcessedValues(nlpService.processAttributesToEntityMap(turNLP));
			}
		} catch (Exception e) {
			logger.error(e);
		}

		if (logger.isDebugEnabled() && turNLP.getEntityMapWithProcessedValues() != null) {
			logger.debug("Result retrieveNLP: {}", turNLP.getEntityMapWithProcessedValues());
		}

	}

	public void fixEntityMapBasedInManualEntity(Map<String, List<String>> entityMapWithProcessedValues) {
		logger.debug("Executing processNLPTerms");
		Map<String, List<String>> processedAttributes = new HashMap<>();
		File userDir = new File(System.getProperty("user.dir"));
		File trainingFile = new File(userDir.getAbsolutePath().concat("/store/nlp/train/train.json"));
		if (trainingFile.exists()) {
			try (BufferedReader rd = new BufferedReader(new FileReader(trainingFile))) {
				TurNLPTrainingBeans turNLPTrainingBeans = new ObjectMapper().readValue(CharStreams.toString(rd), TurNLPTrainingBeans.class);
				Map<String, TurNLPTrainingBean> terms = termMapOfTraining(turNLPTrainingBeans);
				logTermsOfTraining(turNLPTrainingBeans);
				remapEntityMap(entityMapWithProcessedValues, processedAttributes, terms);
			} catch (IOException e) {
				logger.error(e);
			}
			entityMapWithProcessedValues = processedAttributes;
		}
	}

	private void remapEntityMap(Map<String, List<String>> entityMapWithProcessedValues,
			Map<String, List<String>> processedAttributes, Map<String, TurNLPTrainingBean> terms) {
		if (entityMapWithProcessedValues != null) {
			for (Entry<String, List<String>> attribute : entityMapWithProcessedValues.entrySet()) {
				if (attribute.getValue() != null) {
					logger.debug("attribute Value: {}", attribute.getValue());

					if (!processedAttributes.containsKey(attribute.getKey()))
						processedAttributes.put(attribute.getKey(), new ArrayList<>());
					for (Object attributeValueItem : attribute.getValue()) {
						if (terms.containsKey(attributeValueItem.toString().toLowerCase())) {
							useTermOfManualEntityAndRemap(processedAttributes, terms, attribute, attributeValueItem);
						} else {
							processedAttributes.get(attribute.getKey()).add(attributeValueItem.toString());
						}
					}
				}
			}
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
			processedAttributes.get(attribute.getKey())
					.add(attributeValueItem.toString());
	}

	private void remapToNewNer(Map<String, List<String>> processedAttributes, Object attributeValueItem,
			TurNLPTrainingBean term) {
		if (!processedAttributes.containsKey(term.getNer()))
			processedAttributes.put(term.getNer(), new ArrayList<>());
		if (term.getConvertTo() != null)
			processedAttributes.get(term.getNer()).add(term.getConvertTo());
		else
			processedAttributes.get(term.getNer())
					.add(attributeValueItem.toString());
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
