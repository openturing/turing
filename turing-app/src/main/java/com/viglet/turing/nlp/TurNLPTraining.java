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

package com.viglet.turing.nlp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.nlp.bean.TurNLPTrainingBean;
import com.viglet.turing.nlp.bean.TurNLPTrainingBeans;

@Component
public class TurNLPTraining {
	static final Logger logger = LogManager.getLogger(TurNLPTraining.class.getName());

	public Map<String, ArrayList<String>> processNLPTerms(Map<String, Object> attributes) {
		logger.debug("Executing processNLPTerms");
		Map<String, ArrayList<String>> processedAttributes = new HashMap<String, ArrayList<String>>();
		File userDir = new File(System.getProperty("user.dir"));
		File trainingFile = new File(userDir.getAbsolutePath().concat("/store/nlp/train/train.json"));
		Map<String, TurNLPTrainingBean> terms = new HashMap<String, TurNLPTrainingBean>();
		if (trainingFile.exists()) {
			try (BufferedReader rd = new BufferedReader(new FileReader(trainingFile))) {

				String jsonText;
				jsonText = readAll(rd);

				ObjectMapper mapper = new ObjectMapper();
				TurNLPTrainingBeans turNLPTrainingBeans = mapper.readValue(jsonText, TurNLPTrainingBeans.class);
				
				for (TurNLPTrainingBean turNLPTrainingBeanItem : turNLPTrainingBeans.getTerms()) {
					terms.put(turNLPTrainingBeanItem.getTerm().toLowerCase(), turNLPTrainingBeanItem);
				}

				if (logger.isDebugEnabled()) {
					for (TurNLPTrainingBean turNLPTrainingBeanItem : turNLPTrainingBeans.getTerms()) {
						logger.debug(turNLPTrainingBeanItem.toString());
					}
				}
			} catch (IOException e) {
				logger.error(e);
			}
		}
				if (attributes != null) {
					for (Entry<String, Object> attribute : attributes.entrySet()) {
						if (attribute.getValue() != null) {
							logger.debug("attribute Value: " + attribute.getValue().toString());
							if (attribute.getValue() instanceof ArrayList) {
								if (!processedAttributes.containsKey(attribute.getKey()))
									processedAttributes.put(attribute.getKey(), new ArrayList<String>());
								ArrayList<?> attributeList = (ArrayList<?>) attribute.getValue();
								if (attributeList.size() > 0) {
									for (Object attributeItem : attributeList) {
										if (terms.containsKey(attributeItem.toString().toLowerCase())) {
											TurNLPTrainingBean term = terms.get(attributeItem.toString().toLowerCase());
											if (!term.isIgnore()) {
												if (term.getNer() != null) {
													if (!processedAttributes.containsKey(term.getNer()))
														processedAttributes.put(term.getNer(), new ArrayList<String>());
													if (term.getConvertTo() != null)
														processedAttributes.get(term.getNer()).add(term.getConvertTo());
													else
														processedAttributes.get(term.getNer())
																.add(attributeItem.toString());
												} else {
													if (term.getConvertTo() != null)
														processedAttributes.get(attribute.getKey())
																.add(term.getConvertTo());
													else
														processedAttributes.get(attribute.getKey())
																.add(attributeItem.toString());
												}
											}

										} else {
											processedAttributes.get(attribute.getKey()).add(attributeItem.toString());
										}
									}
								}
							}
						}
					}
				}
			
		return processedAttributes;

	}

	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}
}
