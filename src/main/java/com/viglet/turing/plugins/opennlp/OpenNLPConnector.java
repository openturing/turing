package com.viglet.turing.plugins.opennlp;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.xml.transform.TransformerException;

import org.apache.commons.lang.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import com.viglet.turing.entity.VigEntityProcessor;
import com.viglet.turing.nlp.VigNLPResults;
import com.viglet.turing.persistence.model.VigService;
import com.viglet.turing.persistence.model.VigServicesNLPEntity;
import com.viglet.turing.plugins.nlp.NLPImpl;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

public class OpenNLPConnector implements NLPImpl {
	static final Logger logger = LogManager.getLogger(OpenNLPConnector.class.getName());

	List<VigServicesNLPEntity> nlpEntities = null;
	Map<String, JSONArray> entityList = new HashMap<String, JSONArray>();
	public JSONObject json;
	OpenNLPModelManager openNLPModelManager = null;
	VigService vigService = null;
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

	public OpenNLPConnector(VigService vigService) {
		openNLPModelManager = OpenNLPModelManager.getInstance();
		this.vigService = vigService;

		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();

		Query queryNLPEntity = em
				.createQuery(
						"SELECT sne FROM VigServicesNLPEntity sne, VigService s where s.id = :id_service and sne.vigService = s and sne.enabled = :enabled ")
				.setParameter("id_service", vigService.getId()).setParameter("enabled", 1);

		nlpEntities = queryNLPEntity.getResultList();
	}

	public void setText(String text) {
		this.text = text;
	}

	@Override
	public VigNLPResults retrieve(String text) throws TransformerException, Exception {
		this.setText(text);

		String sentences[] = this.sentenceDetect(text);

		for (String sentence : sentences) {
			String tokens[] = this.tokenDetect(sentence);
			this.setSentencesTokens((String[]) ArrayUtils.addAll(this.getSentencesTokens(), tokens));
		}

		VigNLPResults vigNLPResults = new VigNLPResults();
		vigNLPResults.setJsonResult(this.getJSON());

		vigNLPResults.setVigNLPServicesEntity(nlpEntities);

		return vigNLPResults;
	}

	public JSONObject getJSON() {
		JSONObject jsonObject = new JSONObject();
		for (VigServicesNLPEntity entity : nlpEntities) {
			JSONArray entityTerms = this.getEntity(entity.getName());
			if (entityTerms.length() > 0) {
				jsonObject.put(entity.getTurEntity().getCollectionName(), this.getEntity(entity.getName()));
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
			InputStream modelIn = getClass().getClassLoader().getResourceAsStream(entityPath);
			logger.debug("Creating OpenNLP Entity: " + entityPath);
			try {
				TokenNameFinderModel model = new TokenNameFinderModel(modelIn);
				nameFinder = new NameFinderME(model);
				openNLPModelManager.put(entityPath, nameFinder);

			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if (modelIn != null) {
					try {
						modelIn.close();
					} catch (IOException e) {
					}
				}
			}
		}

		String tokens[] = this.getSentencesTokens();
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
	}

	public static String[] sentenceDetect(String text) {
		InputStream modelIn;
		String sentences[] = null;
		modelIn = OpenNLPConnector.class.getClassLoader().getResourceAsStream("/models/opennlp/en/en-sent.bin");

		try {
			SentenceModel model = new SentenceModel(modelIn);
			SentenceDetectorME sentenceDetector = new SentenceDetectorME(model);
			sentences = sentenceDetector.sentDetect(text);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (modelIn != null) {
				try {
					modelIn.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return sentences;
	}

	public String[] tokenDetect(String sentence) {
		InputStream modelIn = null;
		String tokens[] = null;
		try {
			modelIn = getClass().getClassLoader().getResourceAsStream("/models/opennlp/en/en-token.bin");

			TokenizerModel model = new TokenizerModel(modelIn);
			Tokenizer tokenizer = new TokenizerME(model);
			tokens = tokenizer.tokenize(sentence);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (modelIn != null) {
				try {
					modelIn.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return tokens;
	}
}
