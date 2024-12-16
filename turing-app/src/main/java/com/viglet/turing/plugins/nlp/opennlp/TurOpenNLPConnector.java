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

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.plugins.nlp.TurNLPPlugin;
import com.viglet.turing.solr.TurSolrField;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.util.Span;

@Component
public class TurOpenNLPConnector implements TurNLPPlugin {
	static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	@Autowired
	private TurOpenNLPCache turOpenNLPCache;

	@Override
	public Map<String, List<String>> processAttributesToEntityMap(TurNLP turNLP) {
		List<String> sentencesTokens = new ArrayList<>();
		for (Object attrValue : turNLP.getAttributeMapToBeProcessed().values()) {
			String[] sentences = this
					.sentenceDetect(turNLP.getTurNLPInstance(), TurSolrField.convertFieldToString(attrValue).replace("\"", "").replace("'", ""));

			for (String sentence : sentences) {

				String sentencesFormatted = sentence.trim();

				if (sentencesFormatted.endsWith(".")) {
					if (!sentencesFormatted.endsWith(" ."))
						sentencesFormatted = sentencesFormatted.substring(0, sentencesFormatted.length() - 1) + " .";
				} else
					sentencesFormatted = sentencesFormatted + " .";

				logger.debug("OpenNLP Sentence: {}", sentencesFormatted);
				String[] tokens = tokenDetect(turNLP.getTurNLPInstance(), sentencesFormatted + ".");
				Collections.addAll(sentencesTokens, tokens);
			}
		}

		return generateEntityMapFromSentenceTokens(turNLP, sentencesTokens);
	}

	private Map<String, List<String>> generateEntityMapFromSentenceTokens(TurNLP turNLP, List<String> sentencesTokens) {
		Map<String, List<String>> entityMap = new HashMap<>();

		for (TurNLPInstanceEntity nlpInstanceEntity : turNLP.getNlpInstanceEntities()) {
			logger.debug("TurNLPInstanceEntity : {}", nlpInstanceEntity.getName());
			List<String> entityList = this.getEntityList(nlpInstanceEntity.getName(), sentencesTokens);
			if (!entityList.isEmpty()) {
				entityMap.put(nlpInstanceEntity.getTurNLPEntity().getInternalName(), entityList);
			}
		}

		return entityMap;
	}

	private List<String> getEntityList(String entityPath, List<String> sentencesTokens) {
		try {
			NameFinderME nameFinder = null;
			List<String> entities = new ArrayList<>();

			nameFinder = turOpenNLPCache.nameFinderMe(entityPath).getNameFinderME();

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
		return turOpenNLPCache.sentenceDetectorME(turNLPInstance.getLanguage()).getSentenceDetectorME()
				.sentDetect(text);

	}

	private String[] tokenDetect(TurNLPInstance turNLPInstance, String sentence) {
		return turOpenNLPCache.tokenizerME(turNLPInstance.getLanguage()).getTokenizerME().tokenize(sentence);
	}
}
