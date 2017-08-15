package com.viglet.turing.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.viglet.turing.nlp.ListKey;
import com.viglet.turing.nlp.TurNLPRelationType;
import com.viglet.turing.nlp.TurNLPSentence;
import com.viglet.turing.nlp.TurNLPTermAccent;
import com.viglet.turing.nlp.TurNLPTermCase;
import com.viglet.turing.nlp.TurNLPWord;
import com.viglet.turing.persistence.model.nlp.term.TurTerm;
import com.viglet.turing.persistence.model.nlp.term.TurTermRelationFrom;
import com.viglet.turing.persistence.model.nlp.term.TurTermRelationTo;
import com.viglet.turing.persistence.model.nlp.term.TurTermVariation;
import com.viglet.turing.persistence.model.nlp.TurEntity;
import com.viglet.util.TurUtils;

public class TurEntityProcessor {
	static final Logger logger = LogManager.getLogger(TurEntityProcessor.class.getName());
	LinkedHashMap<String, List<String>> entityResults = new LinkedHashMap<String, List<String>>();

	LinkedHashMap<Integer, TurTermVariation> terms = new LinkedHashMap<Integer, TurTermVariation>();

	@SuppressWarnings("unchecked")
	public TurEntityProcessor() {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();

		Query queryTerms = em.createQuery("SELECT t FROM TurTermVariation t");

		List<?> turTerms = (List<?>) queryTerms.getResultList();
		logger.debug("Carregando termos..");
		for (Object turTermVariationObject : turTerms) {
			TurTermVariation turTermVariation = (TurTermVariation) turTermVariationObject;
			terms.put(turTermVariation.getId(), turTermVariation);
		}
		logger.debug("Fim Carregando termos..");
	}

	public LinkedHashMap<String, List<String>> detectTerms(String text) {
		logger.debug("detectTerms....");
		String[] words = TurUtils.removeDuplicateWhiteSpaces(text).split(" ");
		TurNLPSentence turNLPSentence = new TurNLPSentence();

		int[] idx = { 0 };
		Arrays.stream(words).forEach(w -> turNLPSentence.addWord(new TurNLPWord(w, idx[0]++)));

		LinkedHashMap<String, LinkedHashMap<Integer, TurTermVariation>> lhWords = new LinkedHashMap<String, LinkedHashMap<Integer, TurTermVariation>>();
		TurTermVariation[] stringTerms = terms.values().toArray(new TurTermVariation[terms.size()]);

		for (String word : words) {
			String wordLowerCase = TurUtils.stripAccents(word).toLowerCase();
			logger.debug("word: " + word);
			List<TurTermVariation> results = (List<TurTermVariation>) Arrays.stream(stringTerms)
					.filter(t -> t.getNameLower().contains(wordLowerCase)).collect(Collectors.toList());

			LinkedHashMap<Integer, TurTermVariation> hmResults = new LinkedHashMap<Integer, TurTermVariation>();
			Arrays.stream(results.toArray())
					.forEach(r -> hmResults.put(((TurTermVariation) r).getId(), (TurTermVariation) r));
			logger.debug("hmResults.size():" + hmResults.size());
			lhWords.put(wordLowerCase, hmResults);
		}

		LinkedHashMap<ListKey<Integer>, List<Integer>> matches = new LinkedHashMap<ListKey<Integer>, List<Integer>>();
		TurNLPWord turNLPWordPrev = null;
		LinkedHashMap<Integer, TurTermVariation> prevVariations = null;
		for (Object wordObject : turNLPSentence.getWords().values().toArray()) {
			TurNLPWord turNLPWord = (TurNLPWord) wordObject;
			logger.debug("word2: " + turNLPWord.getWord());
			LinkedHashMap<Integer, TurTermVariation> variations = lhWords
					.get(TurUtils.stripAccents(turNLPWord.getWord()).toLowerCase());

			if (prevVariations != null) {
				logger.debug("variations.size(): " + variations.size());
				for (Object variationObject : variations.values().toArray()) {
					TurTermVariation variation = (TurTermVariation) variationObject;
					logger.debug("variation.getId(): " + variation.getId());

					// Validate single word
					if (this.validateTerm(turNLPWord.getWord(), variation)) {
						logger.debug("Single Term was validaded: " + turNLPWord.getWord());
						TurEntity turEntity = this.getEntity(variation);
						if (turEntity != null) {
							if (!entityResults.containsKey(turEntity.getCollectionName())) {
								List<String> lstTerm = new ArrayList<String>();
								lstTerm.add(variation.getName());
								entityResults.put(turEntity.getCollectionName(), lstTerm);
							} else {
								entityResults.get(turEntity.getCollectionName()).add(variation.getName());
							}
							this.getParentTerm(variation.getTurTerm());
						}

					}
					else {
						logger.debug("Single Term wasn't validaded: " + turNLPWord.getWord());
					}
					if (prevVariations.containsKey(variation.getId())) {
						logger.debug("Found!!! " + turNLPWordPrev.getWord() + " " + turNLPWord.getWord() + ":"
								+ variation.getId());
						String wordVariaton = turNLPWordPrev.getWord() + " " + turNLPWord.getWord();

						// Validate 2 or more words
						if (this.validateTerm(wordVariaton, variation)) {
							TurEntity turEntity = this.getEntity(variation);
							if (turEntity != null) {
								if (!entityResults.containsKey(turEntity.getCollectionName())) {
									List<String> lstTerm = new ArrayList<String>();
									lstTerm.add(variation.getName());
									entityResults.put(turEntity.getCollectionName(), lstTerm);
								} else {
									entityResults.get(turEntity.getCollectionName()).add(variation.getName());
								}
								this.getParentTerm(variation.getTurTerm());
							}
						}

						ArrayList<Integer> positionArr = new ArrayList<Integer>();
						positionArr.add(turNLPWordPrev.getPosition());
						positionArr.add(turNLPWord.getPosition());

						ListKey<Integer> positions = new ListKey<Integer>(positionArr);

						if (!matches.containsKey(positions)) {
							List<Integer> matchArray = new ArrayList<Integer>();
							matchArray.add(variation.getId());
							matches.put(positions, matchArray);
						} else {
							matches.get(positions).add(variation.getId());
						}
					}
				}
			} else {
				logger.debug("prevVariations is null");
			}

			turNLPWordPrev = turNLPWord;
			prevVariations = variations;
			for (Integer prevariation : prevVariations.keySet()) {
				logger.debug("prevariation key:" + prevariation);
			}
		}
		logger.debug("Matches...");

		Iterator<?> it = matches.entrySet().iterator();
		List<String> returnList = new ArrayList<String>();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();

			ArrayList<Integer> positions = ((ListKey<Integer>) pair.getKey()).getList();
			List<Integer> ids = (List<Integer>) pair.getValue();
			returnList.addAll(this.checkTermIdBetweenPositions(turNLPSentence, positions, ids, matches));

		}

		return entityResults;
	}

	public List<String> checkTermIdBetweenPositions(TurNLPSentence turNLPSentence, ArrayList<Integer> positions,
			List<Integer> ids, LinkedHashMap<ListKey<Integer>, List<Integer>> matches) {
		List<String> returnList = new ArrayList<String>();
		logger.debug("Current Positions: " + positions.toString());
		if ((positions.get(0).intValue() > 0) && (ids.size() > 0)) {
			logger.debug("Brief Matches...");
			ArrayList<Integer> positionPrevArr = new ArrayList<Integer>();
			positionPrevArr.add(positions.get(0) - 1);
			positionPrevArr.add(positions.get(0));

			logger.debug(positions.toString() + " = " + ids.toString());

			ListKey<Integer> positionsPrev = new ListKey<Integer>(positionPrevArr);
			logger.debug("PositionPrev ... " + positionsPrev.toString());
			if (matches.containsKey(positionsPrev)) {
				List<Integer> filteredIds = new ArrayList<Integer>();
				logger.debug("Contains PositionPrev ... " + positionsPrev.toString());
				for (Integer id : ids) {
					if (matches.containsKey(positionsPrev) && matches.get(positionsPrev).contains(id)) {
						logger.debug("Between matches found:" + positionsPrev.toString() + "|" + positions.toString()
								+ ":" + id);
						filteredIds.add(id);

						StringBuffer wordMatched = new StringBuffer();
						wordMatched.append(this.getWordsByPosition(turNLPSentence, positions.get(0) - 1));
						wordMatched.append(" ");
						wordMatched.append(this.getWordsByPosition(turNLPSentence, positions));
						logger.debug("Compare " + wordMatched.toString() + " : " + terms.get(id).getNameLower());
						if (validateTerm(wordMatched.toString(), terms.get(id))) {
							logger.debug("Match Found");
							this.getParentTerm(terms.get(id).getTurTerm());
							returnList.add(wordMatched.toString() + " -> " + this.getEntity(terms.get(id)).getName());
						} else {
							logger.debug("Match doesn't Found");
						}
					}
					ArrayList<Integer> positionCurrArr = new ArrayList<Integer>();
					positionCurrArr.add(positions.get(0) - 1);
					positionCurrArr.addAll(positions);

					returnList.addAll(
							this.checkTermIdBetweenPositions(turNLPSentence, positionCurrArr, filteredIds, matches));
				}
			} else {
				logger.debug("Not Contains PositionPrev ... " + positionsPrev.toString());
			}
		} else {
			logger.debug("End. First Position or Empty Ids");
		}
		return returnList;
	}

	public TurTerm getParentTerm(TurTerm turTerm) {
		logger.debug("getParentTerm() from " + turTerm.getName());
		for (TurTermRelationFrom relationFrom : turTerm.getTurTermRelationFroms()) {
			logger.debug("getParentTerm() relationFrom Id" + relationFrom.getId());
			if (relationFrom.getRelationType() == TurNLPRelationType.BT.id()) {
				logger.debug("getParentTerm() is BT");
				for (TurTermRelationTo relationTo : relationFrom.getTurTermRelationTos()) {
					logger.debug("getParentTerm() relationTo Id" + relationTo.getId());
					TurTerm parentTerm = relationTo.getTurTerm();
					logger.debug("Parent Term is " + parentTerm.getName());
					if (entityResults.containsKey(parentTerm.getTurEntity().getCollectionName())) {
						entityResults.get(parentTerm.getTurEntity().getCollectionName()).add(parentTerm.getName());
					} else {
						List<String> lstTerm = new ArrayList<String>();
						lstTerm.add(parentTerm.getName());
						entityResults.put(parentTerm.getTurEntity().getCollectionName(), lstTerm);
					}

					this.getParentTerm(relationTo.getTurTerm());
					return relationTo.getTurTerm();
				}
			}
		}
		logger.debug("Parent Term not found");
		return null;
	}

	public TurEntity getEntity(TurTermVariation variation) {
		logger.debug("Entity is " + variation.getTurTerm().getTurEntity().getName());
		return variation.getTurTerm().getTurEntity();
	}

	public String getWordsByPosition(TurNLPSentence turNLPSentence, Integer position) {
		ArrayList<Integer> positions = new ArrayList<Integer>();
		positions.add(position);
		return this.getWordsByPosition(turNLPSentence, positions);
	}

	public String getWordsByPosition(TurNLPSentence turNLPSentence, ArrayList<Integer> positions) {
		StringBuffer words = new StringBuffer();
		ArrayList<TurNLPWord> wordsbyPosition = new ArrayList<TurNLPWord>(turNLPSentence.getWords().values());
		for (Integer position : positions) {
			TurNLPWord turNLPWord = wordsbyPosition.get(position);
			words.append(turNLPWord.getWord() + " ");

		}
		return words.toString().trim();
	}

	public boolean validateTerm(String word, TurTermVariation variation) {
		String wordNoAccent = TurUtils.stripAccents(word);
		String wordLowerCaseNoAccent = wordNoAccent.toLowerCase();
		String wordLowerCaseWithAccent = word.toLowerCase();
		
		String termName = terms.get(variation.getId()).getName();
		String termNameNoAccent = TurUtils.stripAccents(termName);
		String termNameLower = terms.get(variation.getId()).getNameLower();
		

		logger.debug("Validating..");
		if (terms.containsKey(variation.getId())) {
			logger.debug("variation.getId()).getNameLower():" + termNameLower);
			logger.debug("word:" + word);
			if (termNameLower.equals(wordLowerCaseNoAccent)) {
				logger.debug("Validate..." + word + ":" + variation.getId());

				if (variation.getRuleCase() == TurNLPTermCase.CI.id()) {
					logger.debug("Variation is CI");
					if (variation.getRuleAccent() == TurNLPTermAccent.AI.id()) {
						logger.debug("Variation is CI and AI = true");
						return true;
					} else {
						logger.debug("Variation is CI and AS = " + termName.toLowerCase().equals(wordLowerCaseWithAccent));
						return termName.toLowerCase().equals(wordLowerCaseWithAccent);
					}

				} else if (variation.getRuleCase() == TurNLPTermCase.CS.id()) {
					
					if (termNameNoAccent.equals(wordNoAccent)) {
						logger.debug("Variation is CS");
						if (variation.getRuleAccent() == TurNLPTermAccent.AI.id()) {
							logger.debug("Variation is CS and AI = true");
							return true;
						} else {
							logger.debug("Variation is CS and AS = " + termName.equals(word));
							return termName.equals(word);
						}
					} else {
						logger.debug("Variation is CS = false");
						return false;
					}
				} else if (variation.getRuleCase() == TurNLPTermCase.UCS.id()) {
					if (termNameLower.toUpperCase().equals(wordNoAccent)) {
						logger.debug("Variation is UCS");
						if (variation.getRuleAccent() == TurNLPTermAccent.AI.id()) {
							logger.debug("Variation is UCS and AI = true");
							return true;
						} else {
							logger.debug("Variation is UCS and AS = " + termName.toUpperCase().equals(word) );
							return termName.toUpperCase().equals(word);
						}
					} else {
						logger.debug("Variation is UCS = false");
						return false;
					}
				}
			}
		}
		logger.debug("Variation none = false");
		return false;
	}
}
