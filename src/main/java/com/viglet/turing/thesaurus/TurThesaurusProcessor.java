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

package com.viglet.turing.thesaurus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.apache.logging.log4j.LogManager;

import com.viglet.turing.nlp.TurNLPListKey;
import com.viglet.turing.nlp.TurNLPRelationType;
import com.viglet.turing.nlp.TurNLPSentence;
import com.viglet.turing.nlp.TurNLPTermAccent;
import com.viglet.turing.nlp.TurNLPTermCase;
import com.viglet.turing.nlp.TurNLPWord;
import com.viglet.turing.persistence.model.nlp.term.TurTerm;
import com.viglet.turing.persistence.model.nlp.term.TurTermRelationFrom;
import com.viglet.turing.persistence.model.nlp.term.TurTermRelationTo;
import com.viglet.turing.persistence.model.nlp.term.TurTermVariation;
import com.viglet.turing.persistence.repository.nlp.term.TurTermVariationRepository;
import com.viglet.turing.solr.TurSolrField;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.util.TurUtils;

@Component
@ComponentScan
@Transactional
public class TurThesaurusProcessor {
	static final Logger logger = LogManager.getLogger(TurThesaurusProcessor.class.getName());

	@Autowired
	TurTermVariationRepository turTermVariationRepository;
	@Autowired
	TurSolrField turSolrField;
	@Autowired
	TurUtils turUtils;

	// LinkedHashMap<String, List<String>> entityResults = new LinkedHashMap<String,
	// List<String>>();

	LinkedHashMap<String, TurTermVariation> terms = new LinkedHashMap<String, TurTermVariation>();

	public void startup() {
		List<TurTermVariation> turTermVariations = turTermVariationRepository.findAll();

		logger.debug("Carregando termos..");
		for (TurTermVariation turTermVariation : turTermVariations) {
			terms.put(turTermVariation.getId(), turTermVariation);
		}
		logger.debug("Fim Carregando termos..");
	}

	public Map<String, Object> detectTerms(Map<String, Object> attributes) {
		Map<String, List<String>> entityResults = new HashMap<String, List<String>>();
		if (attributes != null) {
			for (Object attrValue : attributes.values()) {
				LinkedHashMap<String, List<String>> termsDetected = this
						.detectTerms(turSolrField.convertFieldToString(attrValue));
				for (Entry<String, List<String>> termDetected : termsDetected.entrySet()) {
					if (entityResults.containsKey(String.format("turing_entity_%s", termDetected.getKey()))) {
						entityResults.get(String.format("turing_entity_%s", termDetected.getKey()))
								.addAll(termDetected.getValue());
					} else {
						List<String> terms = new ArrayList<String>();
						terms.addAll(termDetected.getValue());
						entityResults.put(String.format("turing_entity_%s", termDetected.getKey()), terms);
					}

				}
			}
		}

		Map<String, Object> entityObjectResults = new HashMap<String, Object>();
		for (Entry<String, List<String>> entityResult : entityResults.entrySet()) {
			entityObjectResults.put(entityResult.getKey(), entityResult.getValue());
		}
		return entityObjectResults;
	}

	public TurEntityResults detectTerms(String text) {
		TurEntityResults entityResults = new TurEntityResults();
		logger.debug("detectTerms....");
		String[] words = turUtils.removeDuplicateWhiteSpaces(text).split(" ");
		TurNLPSentence turNLPSentence = new TurNLPSentence();

		int[] idx = { 0 };
		Arrays.stream(words).forEach(w -> turNLPSentence.addWord(new TurNLPWord(w, idx[0]++)));

		LinkedHashMap<String, LinkedHashMap<String, TurTermVariation>> lhWords = new LinkedHashMap<String, LinkedHashMap<String, TurTermVariation>>();
		TurTermVariation[] stringTerms = terms.values().toArray(new TurTermVariation[terms.size()]);

		for (String word : words) {
			String wordLowerCase = turUtils.stripAccents(word).toLowerCase();
			logger.debug("word: " + word);
			List<TurTermVariation> results = (List<TurTermVariation>) Arrays.stream(stringTerms)
					.filter(t -> t.getNameLower().contains(wordLowerCase)).collect(Collectors.toList());

			LinkedHashMap<String, TurTermVariation> hmResults = new LinkedHashMap<String, TurTermVariation>();
			Arrays.stream(results.toArray())
					.forEach(r -> hmResults.put(((TurTermVariation) r).getId(), (TurTermVariation) r));
			logger.debug("hmResults.size():" + hmResults.size());
			lhWords.put(wordLowerCase, hmResults);
		}

		LinkedHashMap<TurNLPListKey<Integer>, List<String>> matches = new LinkedHashMap<TurNLPListKey<Integer>, List<String>>();
		TurNLPWord turNLPWordPrev = null;
		LinkedHashMap<String, TurTermVariation> prevVariations = null;
		for (Object wordObject : turNLPSentence.getWords().values().toArray()) {
			TurNLPWord turNLPWord = (TurNLPWord) wordObject;
			logger.debug("word2: " + turNLPWord.getWord());
			LinkedHashMap<String, TurTermVariation> variations = lhWords
					.get(turUtils.stripAccents(turNLPWord.getWord()).toLowerCase());

			if (prevVariations != null) {
				logger.debug("variations.size(): " + variations.size());
				for (Object variationObject : variations.values().toArray()) {
					TurTermVariation variation = (TurTermVariation) variationObject;
					logger.debug("variation.getId(): " + variation.getId());

					// Validate single word
					if (this.validateTerm(turNLPWord.getWord(), variation)) {
						logger.debug("Single Term was validaded: " + turNLPWord.getWord());
						TurNLPEntity turNLPEntity = this.getEntity(variation);
						if (turNLPEntity != null) {
							logger.debug("turNLPEntity: " + turNLPEntity.getName());
							if (!entityResults.containsKey(turNLPEntity.getCollectionName())) {
								logger.debug(
										"First Item into collection for results: " + turNLPEntity.getCollectionName());
								List<String> lstTerm = new ArrayList<String>();
								lstTerm.add(variation.getName());
								entityResults.put(turNLPEntity.getCollectionName(), lstTerm);
							} else {
								logger.debug(
										"Contains the collection for results: " + turNLPEntity.getCollectionName());
								entityResults.get(turNLPEntity.getCollectionName()).add(variation.getName());
							}

							mergeEntityResultsFromTo(this.getParentTerm(variation.getTurTerm()), entityResults);
							logger.debug("prevVariations not null, entityResults.size(): " + entityResults.size());
						} else {
							logger.debug("turNLPEntity is null");
						}

					} else {
						logger.debug("Single Term wasn't validaded: " + turNLPWord.getWord());
					}
					if (prevVariations.containsKey(variation.getId())) {
						logger.debug("Found!!! " + turNLPWordPrev.getWord() + " " + turNLPWord.getWord() + ":"
								+ variation.getId());
						String wordVariaton = turNLPWordPrev.getWord() + " " + turNLPWord.getWord();

						// Validate 2 or more words
						if (this.validateTerm(wordVariaton, variation)) {
							TurNLPEntity turNLPEntity = this.getEntity(variation);
							if (turNLPEntity != null) {
								if (!entityResults.containsKey(turNLPEntity.getCollectionName())) {
									List<String> lstTerm = new ArrayList<String>();
									lstTerm.add(variation.getName());
									entityResults.put(turNLPEntity.getCollectionName(), lstTerm);
								} else {
									entityResults.get(turNLPEntity.getCollectionName()).add(variation.getName());
								}
								mergeEntityResultsFromTo(this.getParentTerm(variation.getTurTerm()), entityResults);
								logger.debug("prevVariations, entityResults.size(): " + entityResults.size());
							}
						}

						ArrayList<Integer> positionArr = new ArrayList<Integer>();
						positionArr.add(turNLPWordPrev.getPosition());
						positionArr.add(turNLPWord.getPosition());

						TurNLPListKey<Integer> positions = new TurNLPListKey<Integer>(positionArr);

						if (!matches.containsKey(positions)) {
							List<String> matchArray = new ArrayList<String>();
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
			for (String prevariation : prevVariations.keySet()) {
				logger.debug("prevariation key:" + prevariation);
			}
		}
		logger.debug("Matches...");

		Iterator<?> it = matches.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry pair = (Map.Entry) it.next();
			ArrayList<Integer> positions = ((TurNLPListKey<Integer>) pair.getKey()).getList();
			List<String> ids = (List<String>) pair.getValue();
			this.mergeEntityResultsFromTo(this.checkTermIdBetweenPositions(turNLPSentence, positions, ids, matches),
					entityResults);
		}

		logger.debug("entityResults.size(): " + entityResults.size());
		for (Entry<String, List<String>> entry : entityResults.entrySet()) {
			logger.debug("entityREsults entry Key: " + entry.getKey());
			logger.debug("entityREsults entry Value: " + entry.getValue());
		}
		return entityResults;
	}

	public TurEntityResults checkTermIdBetweenPositions(TurNLPSentence turNLPSentence, ArrayList<Integer> positions,
			List<String> ids, LinkedHashMap<TurNLPListKey<Integer>, List<String>> matches) {
		TurEntityResults entityResults = new TurEntityResults();
		logger.debug("Current Positions: " + positions.toString());
		if ((positions.get(0).intValue() > 0) && (ids.size() > 0)) {
			logger.debug("Brief Matches...");
			ArrayList<Integer> positionPrevArr = new ArrayList<Integer>();
			positionPrevArr.add(positions.get(0) - 1);
			positionPrevArr.add(positions.get(0));

			logger.debug(positions.toString() + " = " + ids.toString());

			TurNLPListKey<Integer> positionsPrev = new TurNLPListKey<Integer>(positionPrevArr);
			logger.debug("PositionPrev ... " + positionsPrev.toString());
			if (matches.containsKey(positionsPrev)) {
				List<String> filteredIds = new ArrayList<String>();
				logger.debug("Contains PositionPrev ... " + positionsPrev.toString());
				for (String id : ids) {
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
							String entity = this.getEntity(terms.get(id)).getName().toLowerCase();
							this.mergeEntityResultsFromTo(this.getParentTerm(terms.get(id).getTurTerm()),
									entityResults);
							if (entityResults.containsKey(entity)) {
								entityResults.get(entity).add(wordMatched.toString());
							} else {
								List<String> values = new ArrayList<String>();
								values.add(wordMatched.toString());
								entityResults.put(entity, values);
							}

						} else {
							logger.debug("Match doesn't Found");
						}
					}
					ArrayList<Integer> positionCurrArr = new ArrayList<Integer>();
					positionCurrArr.add(positions.get(0) - 1);
					positionCurrArr.addAll(positions);
					this.mergeEntityResultsFromTo(
							this.checkTermIdBetweenPositions(turNLPSentence, positionCurrArr, filteredIds, matches),
							entityResults);
				}
			} else {
				logger.debug("Not Contains PositionPrev ... " + positionsPrev.toString());
			}
		} else {
			logger.debug("End. First Position or Empty Ids");
		}
		return entityResults;
	}

	private TurEntityResults mergeEntityResultsFromTo(TurEntityResults from, TurEntityResults to) {
		logger.debug("mergeEntity From Size: " + from.size());
		logger.debug("mergeEntity To Size Before: " + to.size());
		Set<Entry<String, List<String>>> fromEntries = from.entrySet();
		for (Entry<String, List<String>> fromEntry : fromEntries) {
			if (to.containsKey(fromEntry.getKey())) {
				logger.debug("mergeEntity Contains");
				logger.debug("mergeEntity To Before: " + to.get(fromEntry.getKey()).toString());
				logger.debug("mergeEntity From: " + fromEntry.getValue());
				to.get(fromEntry.getKey()).addAll(fromEntry.getValue());
				logger.debug("mergeEntity To After: " + to.get(fromEntry.getKey()).toString());
			} else {
				logger.debug("mergeEntity Not Contains");
				List<String> values = new ArrayList<String>();
				values.addAll(fromEntry.getValue());
				to.put(fromEntry.getKey(), values);
			}
		}
		logger.debug("mergeEntity To Size After: " + to.size());
		return to;
	}

	public TurEntityResults getParentTerm(TurTerm turTerm) {
		TurEntityResults entityResults = new TurEntityResults();
		logger.debug("getParentTerm() from " + turTerm.getName());
		for (TurTermRelationFrom relationFrom : turTerm.getTurTermRelationFroms()) {
			logger.debug("getParentTerm() relationFrom Id: " + relationFrom.getId());
			if (relationFrom.getRelationType() == TurNLPRelationType.BT.id()) {
				logger.debug("getParentTerm() is BT");
				for (TurTermRelationTo relationTo : relationFrom.getTurTermRelationTos()) {
					logger.debug("getParentTerm() relationTo Id" + relationTo.getId());
					TurTerm parentTerm = relationTo.getTurTerm();
					logger.debug("Parent Term is " + parentTerm.getName());
					String parentCollection = parentTerm.getTurNLPEntity().getCollectionName();
					if (entityResults.containsKey(parentCollection)) {
						logger.debug("Parent Term Collection: " + parentCollection);
						logger.debug(
								"Parent Term Collection size before: " + entityResults.get(parentCollection).size());
						entityResults.get(parentCollection).add(parentTerm.getName());
						logger.debug(
								"Parent Term Collection size after: " + entityResults.get(parentCollection).size());
					} else {
						logger.debug("Parent Term Collection is new: " + parentCollection);
						List<String> lstTerm = new ArrayList<String>();
						lstTerm.add(parentTerm.getName());
						entityResults.put(parentCollection, lstTerm);
						logger.debug("Parent Term Collection size: " + entityResults.get(parentCollection).size());
					}
					mergeEntityResultsFromTo(this.getParentTerm(relationTo.getTurTerm()), entityResults);
				}
			}
		}
		logger.debug("Parent Term not found");
		return entityResults;

	}

	public TurNLPEntity getEntity(TurTermVariation variation) {
		logger.debug("Entity is " + variation.getTurTerm().getTurNLPEntity().getName());
		return variation.getTurTerm().getTurNLPEntity();
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
		String wordNoAccent = turUtils.stripAccents(word);
		String wordLowerCaseNoAccent = wordNoAccent.toLowerCase();
		String wordLowerCaseWithAccent = word.toLowerCase();

		String termName = terms.get(variation.getId()).getName();
		String termNameNoAccent = turUtils.stripAccents(termName);
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
						logger.debug(
								"Variation is CI and AS = " + termName.toLowerCase().equals(wordLowerCaseWithAccent));
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
							logger.debug("Variation is UCS and AS = " + termName.toUpperCase().equals(word));
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
