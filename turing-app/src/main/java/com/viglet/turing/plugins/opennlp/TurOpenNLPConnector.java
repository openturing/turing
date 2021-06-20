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

package com.viglet.turing.plugins.opennlp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.TransformerException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceEntityRepository;
import com.viglet.turing.plugins.nlp.TurNLPImpl;
import com.viglet.turing.solr.TurSolrField;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.util.Span;

@Component
public class TurOpenNLPConnector implements TurNLPImpl {
	static final Logger logger = LogManager.getLogger(TurOpenNLPConnector.class.getName());

	@Autowired
	private TurNLPInstanceEntityRepository turNLPInstanceEntityRepository;

	@Autowired
	private TurSolrField turSolrField;

	@Autowired
	private TurOpenNLPCache turOpenNLPCache;

	private List<TurNLPInstanceEntity> nlpInstanceEntities = null;

	private List<String> sentencesTokens = new ArrayList<String>();

	private TurNLPInstance turNLPInstance;

	public void startup(TurNLPInstance turNLPInstance) {

		this.turNLPInstance = turNLPInstance;
		nlpInstanceEntities = turNLPInstanceEntityRepository.findByTurNLPInstanceAndEnabled(turNLPInstance, 1);
	}

	@Override
	public Map<String, Object> retrieve(Map<String, Object> attributes) throws TransformerException, Exception {

		for (Object attrValue : attributes.values()) {
			String sentences[] = this.sentenceDetect(
					turSolrField.convertFieldToString(attrValue).replaceAll("\"", "").replaceAll("'", ""));

			for (String sentence : sentences) {

				String sentencesFormatted = sentence.trim();

				if (sentencesFormatted.endsWith(".")) {
					if (!sentencesFormatted.endsWith(" ."))
						sentencesFormatted = sentencesFormatted.substring(0, sentencesFormatted.length() - 1) + " .";
				} else
					sentencesFormatted = sentencesFormatted + " .";

				logger.debug("OpenNLP Sentence: " + sentencesFormatted);
				String tokens[] = this.tokenDetect(sentencesFormatted + ".");
				for (String token : tokens) {
					sentencesTokens.add(token);
				}

			}
		}

		return this.getAttributes();
	}

	public Map<String, Object> getAttributes() {
		Map<String, Object> entityAttributes = new HashMap<String, Object>();

		for (TurNLPInstanceEntity nlpInstanceEntity : nlpInstanceEntities) {
			logger.debug("TurNLPInstanceEntity : " + nlpInstanceEntity.getName());

			if (this.getEntity(nlpInstanceEntity.getName()).size() > 0) {
				entityAttributes.put(nlpInstanceEntity.getTurNLPEntity().getInternalName(),
						this.getEntity(nlpInstanceEntity.getName()));
			}
		}

		return entityAttributes;
	}

	public List<String> getEntity(String entityPath) {
		try {
			NameFinderME nameFinder = null;
			List<String> entities = new ArrayList<String>();

			nameFinder = turOpenNLPCache.nameFinderMe(entityPath).getNameFinderME();

			String[] tokens = this.sentencesTokens.toArray(new String[0]);
			if (tokens != null) {
				Span nameSpans[] = nameFinder.find(tokens);

				for (Span nameSpan : nameSpans) {
					String name = "";
					for (int i = nameSpan.getStart(); i < nameSpan.getEnd(); i++) {
						name += tokens[i];
						if (i < nameSpan.getEnd() - 1)
							name += " ";
					}
					entities.add(name);
				}

				return entities;
			} else {
				logger.debug("Sentences returns null of OpenNLP Entity " + entityPath);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;

	}

	public String[] sentenceDetect(String text) {
		return turOpenNLPCache.sentenceDetectorME(this.turNLPInstance.getLanguage()).getSentenceDetectorME()
				.sentDetect(text);

	}

	public String[] tokenDetect(String sentence) {
		return turOpenNLPCache.tokenizerME(this.turNLPInstance.getLanguage()).getTokenizerME().tokenize(sentence);
	}
}
