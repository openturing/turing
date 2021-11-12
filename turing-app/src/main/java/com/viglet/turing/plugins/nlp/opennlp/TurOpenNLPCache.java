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

package com.viglet.turing.plugins.nlp.opennlp;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

@Component
public class TurOpenNLPCache {
	static final Logger logger = LogManager.getLogger(TurOpenNLPCache.class.getName());

	@Cacheable("nlpName")
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

	@Cacheable("nlpSentence")
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

	@Cacheable("nlpToken")
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
