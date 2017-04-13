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
import org.json.JSONArray;

import com.viglet.turing.nlp.ListKey;
import com.viglet.turing.nlp.VigNLPRelationType;
import com.viglet.turing.nlp.VigNLPSentence;
import com.viglet.turing.nlp.VigNLPTermAccent;
import com.viglet.turing.nlp.VigNLPTermCase;
import com.viglet.turing.nlp.VigNLPWord;
import com.viglet.turing.persistence.model.VigEntity;
import com.viglet.turing.persistence.model.VigTerm;
import com.viglet.turing.persistence.model.VigTermRelationFrom;
import com.viglet.turing.persistence.model.VigTermRelationTo;
import com.viglet.turing.persistence.model.VigTermVariation;
import com.viglet.util.VigUtils;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

// Only Words in MySQL: SELECT * FROM `vigTermVariation` WHERE name_lower REGEXP '[[:<:]]abelha[[:>:]]'

public class VigEntityProcessor {
	static final Logger logger = LogManager.getLogger(VigEntityProcessor.class.getName());
	LinkedHashMap<String, List<String>> entityResults = new LinkedHashMap<String, List<String>>();

	LinkedHashMap<Integer, VigTermVariation> terms = new LinkedHashMap<Integer, VigTermVariation>();

	@SuppressWarnings("unchecked")
	public VigEntityProcessor() {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();

		Query queryTerms = em.createQuery("SELECT t FROM VigTermVariation t");

		List<?> vigTerms = (List<?>) queryTerms.getResultList();
		logger.debug("Carregando termos..");
		for (Object vigTermVariationObject : vigTerms) {
			VigTermVariation vigTermVariation = (VigTermVariation) vigTermVariationObject;
			terms.put(vigTermVariation.getId(), vigTermVariation);
		}
		logger.debug("Fim Carregando termos..");
	}

	public LinkedHashMap<String, List<String>> detectTerms(String text) {
		logger.debug("detectTerms....");
		String[] words = VigUtils.removeDuplicateWhiteSpaces(text).split(" ");
		VigNLPSentence vigNLPSentence = new VigNLPSentence();

		int[] idx = { 0 };
		Arrays.stream(words).forEach(w -> vigNLPSentence.addWord(new VigNLPWord(w, idx[0]++)));

		LinkedHashMap<String, LinkedHashMap<Integer, VigTermVariation>> lhWords = new LinkedHashMap<String, LinkedHashMap<Integer, VigTermVariation>>();
		VigTermVariation[] stringTerms = terms.values().toArray(new VigTermVariation[terms.size()]);

		for (String word : words) {
			String wordLowerCase = VigUtils.stripAccents(word).toLowerCase();
			logger.debug("word: " + word);
			List<VigTermVariation> results = (List<VigTermVariation>) Arrays.stream(stringTerms)
					.filter(t -> t.getNameLower().contains(wordLowerCase)).collect(Collectors.toList());

			LinkedHashMap<Integer, VigTermVariation> hmResults = new LinkedHashMap<Integer, VigTermVariation>();
			Arrays.stream(results.toArray())
					.forEach(r -> hmResults.put(((VigTermVariation) r).getId(), (VigTermVariation) r));
			logger.debug("hmResults.size():" + hmResults.size());
			lhWords.put(wordLowerCase, hmResults);
		}

		LinkedHashMap<ListKey<Integer>, List<Integer>> matches = new LinkedHashMap<ListKey<Integer>, List<Integer>>();
		VigNLPWord vigNLPWordPrev = null;
		LinkedHashMap<Integer, VigTermVariation> prevVariations = null;
		for (Object wordObject : vigNLPSentence.getWords().values().toArray()) {
			VigNLPWord vigNLPWord = (VigNLPWord) wordObject;
			logger.debug("word2: " + vigNLPWord.getWord());
			LinkedHashMap<Integer, VigTermVariation> variations = lhWords
					.get(VigUtils.stripAccents(vigNLPWord.getWord()).toLowerCase());

			if (prevVariations != null) {
				logger.debug("variations.size(): " + variations.size());
				for (Object variationObject : variations.values().toArray()) {
					VigTermVariation variation = (VigTermVariation) variationObject;
					logger.debug("variation.getId(): " + variation.getId());

					// Validate single word
					if (this.validateTerm(vigNLPWord.getWord(), variation)) {
						logger.debug("Single Term was validaded: " + vigNLPWord.getWord());
						VigEntity vigEntity = this.getEntity(variation);
						if (vigEntity != null) {
							if (!entityResults.containsKey(vigEntity.getCollectionName())) {
								List<String> lstTerm = new ArrayList<String>();
								lstTerm.add(variation.getName());
								entityResults.put(vigEntity.getCollectionName(), lstTerm);
							} else {
								entityResults.get(vigEntity.getCollectionName()).add(variation.getName());
							}
							this.getParentTerm(variation.getVigTerm());
						}

					}
					else {
						logger.debug("Single Term wasn't validaded: " + vigNLPWord.getWord());
					}
					if (prevVariations.containsKey(variation.getId())) {
						logger.debug("Found!!! " + vigNLPWordPrev.getWord() + " " + vigNLPWord.getWord() + ":"
								+ variation.getId());
						String wordVariaton = vigNLPWordPrev.getWord() + " " + vigNLPWord.getWord();

						// Validate 2 or more words
						if (this.validateTerm(wordVariaton, variation)) {
							VigEntity vigEntity = this.getEntity(variation);
							if (vigEntity != null) {
								if (!entityResults.containsKey(vigEntity.getCollectionName())) {
									List<String> lstTerm = new ArrayList<String>();
									lstTerm.add(variation.getName());
									entityResults.put(vigEntity.getCollectionName(), lstTerm);
								} else {
									entityResults.get(vigEntity.getCollectionName()).add(variation.getName());
								}
								this.getParentTerm(variation.getVigTerm());
							}
						}

						ArrayList<Integer> positionArr = new ArrayList<Integer>();
						positionArr.add(vigNLPWordPrev.getPosition());
						positionArr.add(vigNLPWord.getPosition());

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

			vigNLPWordPrev = vigNLPWord;
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
			returnList.addAll(this.checkTermIdBetweenPositions(vigNLPSentence, positions, ids, matches));

		}

		return entityResults;
	}

	public List<String> checkTermIdBetweenPositions(VigNLPSentence vigNLPSentence, ArrayList<Integer> positions,
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
						wordMatched.append(this.getWordsByPosition(vigNLPSentence, positions.get(0) - 1));
						wordMatched.append(" ");
						wordMatched.append(this.getWordsByPosition(vigNLPSentence, positions));
						logger.debug("Compare " + wordMatched.toString() + " : " + terms.get(id).getNameLower());
						if (validateTerm(wordMatched.toString(), terms.get(id))) {
							logger.debug("Match Found");
							this.getParentTerm(terms.get(id).getVigTerm());
							returnList.add(wordMatched.toString() + " -> " + this.getEntity(terms.get(id)).getName());
						} else {
							logger.debug("Match doesn't Found");
						}
					}
					ArrayList<Integer> positionCurrArr = new ArrayList<Integer>();
					positionCurrArr.add(positions.get(0) - 1);
					positionCurrArr.addAll(positions);

					returnList.addAll(
							this.checkTermIdBetweenPositions(vigNLPSentence, positionCurrArr, filteredIds, matches));
				}
			} else {
				logger.debug("Not Contains PositionPrev ... " + positionsPrev.toString());
			}
		} else {
			logger.debug("End. First Position or Empty Ids");
		}
		return returnList;
	}

	public VigTerm getParentTerm(VigTerm vigTerm) {
		logger.debug("getParentTerm() from " + vigTerm.getName());
		for (VigTermRelationFrom relationFrom : vigTerm.getVigTermRelationFroms()) {
			logger.debug("getParentTerm() relationFrom Id" + relationFrom.getId());
			if (relationFrom.getRelationType() == VigNLPRelationType.BT.id()) {
				logger.debug("getParentTerm() is BT");
				for (VigTermRelationTo relationTo : relationFrom.getVigTermRelationTos()) {
					logger.debug("getParentTerm() relationTo Id" + relationTo.getId());
					VigTerm parentTerm = relationTo.getVigTerm();
					logger.debug("Parent Term is " + parentTerm.getName());
					if (entityResults.containsKey(parentTerm.getVigEntity().getCollectionName())) {
						entityResults.get(parentTerm.getVigEntity().getCollectionName()).add(parentTerm.getName());
					} else {
						List<String> lstTerm = new ArrayList<String>();
						lstTerm.add(parentTerm.getName());
						entityResults.put(parentTerm.getVigEntity().getCollectionName(), lstTerm);
					}

					this.getParentTerm(relationTo.getVigTerm());
					return relationTo.getVigTerm();
				}
			}
		}
		logger.debug("Parent Term not found");
		return null;
	}

	public VigEntity getEntity(VigTermVariation variation) {
		logger.debug("Entity is " + variation.getVigTerm().getVigEntity().getName());
		return variation.getVigTerm().getVigEntity();
	}

	public String getWordsByPosition(VigNLPSentence vigNLPSentence, Integer position) {
		ArrayList<Integer> positions = new ArrayList<Integer>();
		positions.add(position);
		return this.getWordsByPosition(vigNLPSentence, positions);
	}

	public String getWordsByPosition(VigNLPSentence vigNLPSentence, ArrayList<Integer> positions) {
		StringBuffer words = new StringBuffer();
		ArrayList<VigNLPWord> wordsbyPosition = new ArrayList<VigNLPWord>(vigNLPSentence.getWords().values());
		for (Integer position : positions) {
			VigNLPWord vigNLPWord = wordsbyPosition.get(position);
			words.append(vigNLPWord.getWord() + " ");

		}
		return words.toString().trim();
	}

	public boolean validateTerm(String word, VigTermVariation variation) {
		String wordNoAccent = VigUtils.stripAccents(word);
		String wordLowerCaseNoAccent = wordNoAccent.toLowerCase();
		String wordLowerCaseWithAccent = word.toLowerCase();
		
		String termName = terms.get(variation.getId()).getName();
		String termNameNoAccent = VigUtils.stripAccents(termName);
		String termNameLower = terms.get(variation.getId()).getNameLower();
		

		logger.debug("Validating..");
		if (terms.containsKey(variation.getId())) {
			logger.debug("variation.getId()).getNameLower():" + termNameLower);
			logger.debug("word:" + word);
			if (termNameLower.equals(wordLowerCaseNoAccent)) {
				logger.debug("Validate..." + word + ":" + variation.getId());

				if (variation.getRuleCase() == VigNLPTermCase.CI.id()) {
					logger.debug("Variation is CI");
					if (variation.getRuleAccent() == VigNLPTermAccent.AI.id()) {
						logger.debug("Variation is CI and AI = true");
						return true;
					} else {
						logger.debug("Variation is CI and AS = " + termName.toLowerCase().equals(wordLowerCaseWithAccent));
						return termName.toLowerCase().equals(wordLowerCaseWithAccent);
					}

				} else if (variation.getRuleCase() == VigNLPTermCase.CS.id()) {
					
					if (termNameNoAccent.equals(wordNoAccent)) {
						logger.debug("Variation is CS");
						if (variation.getRuleAccent() == VigNLPTermAccent.AI.id()) {
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
				} else if (variation.getRuleCase() == VigNLPTermCase.UCS.id()) {
					if (termNameLower.toUpperCase().equals(wordNoAccent)) {
						logger.debug("Variation is UCS");
						if (variation.getRuleAccent() == VigNLPTermAccent.AI.id()) {
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
