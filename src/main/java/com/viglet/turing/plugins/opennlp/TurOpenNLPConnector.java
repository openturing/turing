package com.viglet.turing.plugins.opennlp;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.xml.transform.TransformerException;

import org.apache.commons.lang.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.nlp.TurNLPResults;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceEntityRepository;
import com.viglet.turing.plugins.nlp.TurNLPImpl;

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
	TurNLPInstanceEntityRepository turNLPInstanceEntityRepository;
	@Autowired
	ServletContext context;

	List<TurNLPInstanceEntity> nlpInstanceEntities = null;
	Map<String, JSONArray> entityList = new HashMap<String, JSONArray>();
	public JSONObject json;
	TurOpenNLPModelManager openNLPModelManager = null;
	TurNLPInstance turNLPInstance = null;
	private String text = null;
	private String[] sentencesTokens = {};

	public String[] getSentencesTokens() {
		return sentencesTokens;
	}

	public void setSentencesTokens(String[] sentencesTokens) {
		this.sentencesTokens = sentencesTokens;
	}

	public String getText() {
		return text;
	}

	public void startup(TurNLPInstance turNLPInstance) {

		openNLPModelManager = TurOpenNLPModelManager.getInstance();
		this.turNLPInstance = turNLPInstance;

		nlpInstanceEntities = turNLPInstanceEntityRepository.findByTurNLPInstance(turNLPInstance);
	}

	public void setText(String text) {
		this.text = text;
	}

	@Override
	public TurNLPResults retrieve(String text) throws TransformerException, Exception {
		this.setText(text);

		String sentences[] = this.sentenceDetect(text);
	
		for (String sentence : sentences) {
			logger.debug("OpenNLP Sentence : " + sentence);
			String tokens[] = this.tokenDetect(sentence);
			this.setSentencesTokens((String[]) ArrayUtils.addAll(this.getSentencesTokens(), tokens));
			
		}

		TurNLPResults turNLPResults = new TurNLPResults();
		turNLPResults.setJsonResult(this.getJSON());

		turNLPResults.setTurNLPInstanceEntities(nlpInstanceEntities);

		return turNLPResults;
	}

	public JSONObject getJSON() throws JSONException {
		JSONObject jsonObject = new JSONObject();
		for (TurNLPInstanceEntity nlpInstanceEntity : nlpInstanceEntities) {
			logger.debug("TurNLPInstanceEntity : " + nlpInstanceEntity.getName());
			JSONArray entityTerms = this.getEntity(nlpInstanceEntity.getName());
			if (entityTerms.length() > 0) {
				jsonObject.put(nlpInstanceEntity.getTurNLPEntity().getCollectionName(),
						this.getEntity(nlpInstanceEntity.getName()));
			}
		}
		jsonObject.put("nlp", "OpenNLP");

		return jsonObject;
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

		String tokens[] = this.getSentencesTokens();
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
		File modelIn;
		String sentences[] = null;
		try {
			File userDir = new File(System.getProperty("user.dir"));
			modelIn = new File(userDir.getAbsolutePath().concat("/models/opennlp/en/en-sent.bin"));
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
			modelIn = new File(userDir.getAbsolutePath().concat("/models/opennlp/en/en-token.bin"));
			TokenizerModel model = new TokenizerModel(modelIn);
			Tokenizer tokenizer = new TokenizerME(model);
			tokens = tokenizer.tokenize(sentence);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return tokens;
	}
}
