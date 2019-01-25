package com.viglet.turing.plugins.opennlp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.TransformerException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceEntityRepository;
import com.viglet.turing.plugins.nlp.TurNLPImpl;
import com.viglet.turing.solr.TurSolrField;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

@Component
public class TurOpenNLPConnector implements TurNLPImpl {
	static final Logger logger = LogManager.getLogger(TurOpenNLPConnector.class.getName());

	@Autowired
	private TurNLPInstanceEntityRepository turNLPInstanceEntityRepository;
	@Autowired
	private TurSolrField turSolrField;

	private TurNLPInstance turNLPInstance = null;
	private List<TurNLPInstanceEntity> nlpInstanceEntities = null;
	private TurOpenNLPModelManager openNLPModelManager = null;
	private List<String> sentencesTokens = new ArrayList<String>();

	public void startup(TurNLPInstance turNLPInstance) {

		openNLPModelManager = TurOpenNLPModelManager.getInstance();
		this.turNLPInstance = turNLPInstance;
		nlpInstanceEntities = turNLPInstanceEntityRepository.findByTurNLPInstanceAndEnabled(turNLPInstance, 1);
	}

	@Override
	public Map<String, Object> retrieve(Map<String, Object> attributes) throws TransformerException, Exception {

		for (Object attrValue : attributes.values()) {
			String sentences[] = this.sentenceDetect(turSolrField.convertFieldToString(attrValue).replaceAll("\"", "").replaceAll("'", ""));

			for (String sentence : sentences) {

				String sentencesFormatted = sentence.trim();

				if (sentencesFormatted.endsWith(".")) {
					if (!sentencesFormatted.endsWith(" .")) {
						sentencesFormatted = sentencesFormatted.substring(0, sentencesFormatted.length() - 1) + " .";
					}
				} else {

					sentencesFormatted = sentencesFormatted + " .";
				}
				;
				logger.debug("OpenNLP Sentence: " + sentencesFormatted);
				String tokens[] = this.tokenDetect(sentencesFormatted + ".");
				for (String token : tokens) {
					sentencesTokens.add(token);
				}

			}
		}

		return this.getAttributes();
	}

	public Map<String, Object> getAttributes() throws JSONException {
		Map<String, Object> entityAttributes = new HashMap<String, Object>();

		for (TurNLPInstanceEntity nlpInstanceEntity : nlpInstanceEntities) {
			logger.debug("TurNLPInstanceEntity : " + nlpInstanceEntity.getName());

			if (this.getEntity(nlpInstanceEntity.getName()).length() > 0) {
				entityAttributes.put(nlpInstanceEntity.getTurNLPEntity().getInternalName(),
						this.getEntity(nlpInstanceEntity.getName()));
			}
		}

		return entityAttributes;
	}

	public JSONArray getEntity(String entityPath) {
		NameFinderME nameFinder = null;
		JSONArray jsonEntity = new JSONArray();

		if (openNLPModelManager.exists(entityPath)) {
			logger.debug("Loading OpenNLP Entity: " + entityPath);
			nameFinder = openNLPModelManager.get(entityPath);
		} else {
			try {
				File modelIn = new File(entityPath);
				logger.debug("Creating OpenNLP Entity: " + entityPath);

				TokenNameFinderModel model = new TokenNameFinderModel(modelIn);
				nameFinder = new NameFinderME(model);
				openNLPModelManager.put(entityPath, nameFinder);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

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
				jsonEntity.put(name);
			}

			return jsonEntity;
		} else {
			logger.debug("Sentences returns null of OpenNLP Entity " + entityPath);
			return null;
		}
	}

	public String[] sentenceDetect(String text) {
		File modelIn = null;
		String sentences[] = null;
		try {
			File userDir = new File(System.getProperty("user.dir"));
			if (this.turNLPInstance.getLanguage().equals("en_US")) {
				modelIn = new File(userDir.getAbsolutePath().concat("/models/opennlp/en/en-sent.bin"));
			} else if (this.turNLPInstance.getLanguage().equals("pt_BR")) {
				modelIn = new File(userDir.getAbsolutePath().concat("/models/opennlp/pt/pt-sent.bin"));
			}
			SentenceModel model = new SentenceModel(modelIn);
			SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);
			sentences = sentenceDetector.sentDetect(text);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return sentences;
	}

	public String[] tokenDetect(String sentence) {
		File modelIn = null;
		String tokens[] = null;
		try {
			File userDir = new File(System.getProperty("user.dir"));
			if (this.turNLPInstance.getLanguage().equals("en_US")) {
				modelIn = new File(userDir.getAbsolutePath().concat("/models/opennlp/en/en-token.bin"));
			} else if (this.turNLPInstance.getLanguage().equals("pt_BR")) {
				modelIn = new File(userDir.getAbsolutePath().concat("/models/opennlp/pt/pt-token.bin"));
			}
			TokenizerModel model = new TokenizerModel(modelIn);
			Tokenizer tokenizer = new TokenizerME(model);
			tokens = tokenizer.tokenize(sentence);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return tokens;
	}
}
