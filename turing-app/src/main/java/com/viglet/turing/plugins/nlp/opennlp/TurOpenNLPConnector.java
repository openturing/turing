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

package com.viglet.turing.plugins.nlp.opennlp;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import com.viglet.turing.nlp.TurNLPEntityRequest;
import com.viglet.turing.nlp.TurNLPRequest;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.plugins.nlp.TurNLPPlugin;
import com.viglet.turing.solr.TurSolrField;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

@Component
public class TurOpenNLPConnector implements TurNLPPlugin {
	static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	@Override
	public Map<String, List<String>> processAttributesToEntityMap(TurNLPRequest turNLPRequest) {
		List<String> sentencesTokens = new ArrayList<>();
		for (Object attrValue : turNLPRequest.getData().values()) {
			String[] sentences = this.sentenceDetect(turNLPRequest.getTurNLPInstance(),
					TurSolrField.convertFieldToString(attrValue).replace("\"", "").replace("'", ""));

			for (String sentence : sentences) {
				String sentencesFormatted = sentence.trim();
				if (sentencesFormatted.endsWith(".")) {
					if (!sentencesFormatted.endsWith(" ."))
						sentencesFormatted = sentencesFormatted.substring(0, sentencesFormatted.length() - 1) + " .";
				} else
					sentencesFormatted = sentencesFormatted + " .";

				logger.debug("OpenNLP Sentence: {}", sentencesFormatted);
				String[] tokens = tokenDetect(turNLPRequest.getTurNLPInstance(), sentencesFormatted + ".");
				Collections.addAll(sentencesTokens, tokens);
			}
		}

		return generateEntityMapFromSentenceTokens(turNLPRequest, sentencesTokens);
	}

	private Map<String, List<String>> generateEntityMapFromSentenceTokens(TurNLPRequest turNLPRequest, List<String> sentencesTokens) {
		Map<String, List<String>> entityMap = new HashMap<>();

		for (TurNLPEntityRequest turNLPEntityRequest : turNLPRequest.getEntities()) {
			logger.debug("TurNLPInstanceEntity : {}", turNLPEntityRequest);
			List<String> entityList = this.getEntityList(turNLPEntityRequest.getName(), sentencesTokens);
			if (!entityList.isEmpty()) {
				entityMap.put(turNLPEntityRequest.getTurNLPVendorEntity().getTurNLPEntity().getInternalName(), entityList);
			}
		}

		return entityMap;
	}

	private List<String> getEntityList(String entityPath, List<String> sentencesTokens) {
		try {
			NameFinderME nameFinder = null;
			List<String> entities = new ArrayList<>();

			nameFinder = nameFinderMe(entityPath).getNameFinderME();

			String[] tokens = sentencesTokens.toArray(new String[0]);
			if (tokens != null) {
				Span[] nameSpans = nameFinder.find(tokens);

				for (Span nameSpan : nameSpans) {
					StringBuilder name = new StringBuilder();
					for (int i = nameSpan.getStart(); i < nameSpan.getEnd(); i++) {
						name.append(tokens[i]);
						if (i < nameSpan.getEnd() - 1)
							name.append(" ");
					}
					entities.add(name.toString());
				}

				return entities;
			} else {
				logger.debug("Sentences returns null of OpenNLP Entity {}", entityPath);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

		return Collections.emptyList();

	}

	public String[] sentenceDetect(TurNLPInstance turNLPInstance, String text) {
		return sentenceDetectorME(turNLPInstance.getLanguage()).getSentenceDetectorME().sentDetect(text);

	}

	private String[] tokenDetect(TurNLPInstance turNLPInstance, String sentence) {
		TurTokenizerME turTokenizerME = tokenizerME(turNLPInstance.getLanguage());
		if (turTokenizerME != null && turTokenizerME.getTokenizerME() != null) {
			return turTokenizerME.getTokenizerME().tokenize(sentence);
		}
		return new String[0];
	}

	public TurNameFinderME nameFinderMe(String entityPath) {
		logger.debug("Creating OpenNLP Entity: {}", entityPath);
		File modelIn = new File(entityPath);

		TokenNameFinderModel model;
		try {
			model = new TokenNameFinderModel(modelIn);
			NameFinderME nameFinderME = new NameFinderME(model);
			return new TurNameFinderME(nameFinderME);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}

		return null;

	}

	public TurSentenceDetectorME sentenceDetectorME(String language) {
		try {
			File modelIn = null;
			File userDir = new File(System.getProperty("user.dir"));
			if (language.equals("en_US")) {
				modelIn = new File(userDir.getAbsolutePath().concat("/models/opennlp/en/en-sent.bin"));
			} else if (language.equals("pt_BR")) {
				modelIn = new File(userDir.getAbsolutePath().concat("/models/opennlp/pt/pt-sent.bin"));
			}
			SentenceModel model = new SentenceModel(modelIn);
			SentenceDetectorME sentenceDetectorME = new SentenceDetectorME(model);
			return new TurSentenceDetectorME(sentenceDetectorME);

		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}

	public TurTokenizerME tokenizerME(String language) {
		try {
			File modelIn = null;
			File userDir = new File(System.getProperty("user.dir"));
			if (language.equals("en_US")) {
				modelIn = new File(userDir.getAbsolutePath().concat("/models/opennlp/en/en-token.bin"));
			} else if (language.equals("pt_BR")) {
				modelIn = new File(userDir.getAbsolutePath().concat("/models/opennlp/pt/pt-token.bin"));
			}
			TokenizerModel model = new TokenizerModel(modelIn);
			TokenizerME tokenizerME = new TokenizerME(model);
			return new TurTokenizerME(tokenizerME);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}

}
