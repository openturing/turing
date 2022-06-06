/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.thesaurus;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.apache.logging.log4j.LogManager;

import com.viglet.turing.commons.utils.TurCommonsUtils;
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

@Component
@ComponentScan
@Transactional
public class TurThesaurusProcessor {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	@Autowired
	private TurTermVariationRepository turTermVariationRepository;

	private Map<String, TurTermVariation> terms = new LinkedHashMap<>();

	public void startup() {
		List<TurTermVariation> turTermVariations = turTermVariationRepository.findAll();

		logger.debug("Loading terms..");
		for (TurTermVariation turTermVariation : turTermVariations) {
			terms.put(turTermVariation.getId(), turTermVariation);
		}
		logger.debug("Loading of terms was finished..");
	}

	public Map<String, Object> detectTerms(Map<String, Object> attributes) {
		Map<String, List<String>> entityResults = new HashMap<>();
		if (attributes != null) {
			for (Object attrValue : attributes.values()) {
				Map<String, List<String>> termsDetected = this
						.detectTerms(TurSolrField.convertFieldToString(attrValue));
				for (Entry<String, List<String>> termDetected : termsDetected.entrySet()) {
					if (entityResults.containsKey(nlpEntityAttribute(termDetected))) {
						entityResults.get(nlpEntityAttribute(termDetected)).addAll(termDetected.getValue());
					} else {
						List<String> termList = new ArrayList<>();
						termList.addAll(termDetected.getValue());
						entityResults.put(nlpEntityAttribute(termDetected), termList);
					}

				}
			}
		}

		Map<String, Object> entityObjectResults = new HashMap<>();
		for (Entry<String, List<String>> entityResult : entityResults.entrySet()) {
			entityObjectResults.put(entityResult.getKey(), entityResult.getValue());
		}
		return entityObjectResults;
	}

	private String nlpEntityAttribute(Entry<String, List<String>> termDetected) {
		return String.format("turing_entity_%s", termDetected.getKey());
	}

	public TurEntityResults detectTerms(String text) {
		TurEntityResults entityResults = new TurEntityResults();
		logger.debug("detectTerms....");
		String[] words = TurCommonsUtils.removeDuplicateWhiteSpaces(text).split(" ");
		TurNLPSentence turNLPSentence = new TurNLPSentence();

		int[] idx = { 0 };
		Arrays.stream(words).forEach(w -> turNLPSentence.addWord(new TurNLPWord(w, idx[0]++)));

		Map<String, Map<String, TurTermVariation>> lhWords = new LinkedHashMap<>();
		TurTermVariation[] stringTerms = terms.values().toArray(new TurTermVariation[terms.size()]);

		for (String word : words) {
			String wordLowerCase = TurCommonsUtils.stripAccents(word).toLowerCase();
			logger.debug("word: {}", word);
			List<TurTermVariation> results = Arrays.stream(stringTerms)
					.filter(t -> t.getNameLower().contains(wordLowerCase)).toList();

			Map<String, TurTermVariation> hmResults = new LinkedHashMap<>();
			Arrays.stream(results.toArray())
					.forEach(r -> hmResults.put(((TurTermVariation) r).getId(), (TurTermVariation) r));
			logger.debug("hmResults.size(): {}", hmResults.size());
			lhWords.put(wordLowerCase, hmResults);
		}

		Map<TurNLPListKey<Integer>, List<String>> matches = new LinkedHashMap<>();
		TurNLPWord turNLPWordPrev = null;
		Map<String, TurTermVariation> prevVariations = null;
		for (Object wordObject : turNLPSentence.getWords().values().toArray()) {
			TurNLPWord turNLPWord = (TurNLPWord) wordObject;
			logger.debug("word2: {}", turNLPWord.getWord());
			Map<String, TurTermVariation> variations = lhWords
					.get(TurCommonsUtils.stripAccents(turNLPWord.getWord()).toLowerCase());

			if (prevVariations != null) {
				logger.debug("variations.size(): {}", variations.size());
				for (Object variationObject : variations.values().toArray()) {
					setVariations(entityResults, matches, turNLPWordPrev, prevVariations, turNLPWord, variationObject);
				}
			} else {
				logger.debug("prevVariations is null");
			}

			turNLPWordPrev = turNLPWord;
			prevVariations = variations;
			for (String prevariation : prevVariations.keySet()) {
				logger.debug("prevariation key: {}", prevariation);
			}
		}
		setMatches(entityResults, turNLPSentence, matches);

		logResults(entityResults);
		return entityResults;
	}

	private void setVariations(TurEntityResults entityResults, Map<TurNLPListKey<Integer>, List<String>> matches,
			TurNLPWord turNLPWordPrev, Map<String, TurTermVariation> prevVariations, TurNLPWord turNLPWord,
			Object variationObject) {
		TurTermVariation variation = (TurTermVariation) variationObject;
		logger.debug("variation.getId(): {}", variation.getId());

		singleWord(entityResults, turNLPWord, variation);
		manyWords(entityResults, matches, turNLPWordPrev, prevVariations, turNLPWord, variation);
	}

	private void manyWords(TurEntityResults entityResults, Map<TurNLPListKey<Integer>, List<String>> matches,
			TurNLPWord turNLPWordPrev, Map<String, TurTermVariation> prevVariations, TurNLPWord turNLPWord,
			TurTermVariation variation) {
		if (prevVariations.containsKey(variation.getId())) {
			logger.debug("Found {} {}: {} ", turNLPWordPrev.getWord(), turNLPWord.getWord(),
					variation.getId());
			String wordVariaton = turNLPWordPrev.getWord() + " " + turNLPWord.getWord();

			// Validate 2 or more words
			if (this.validateTerm(wordVariaton, variation)) {
				TurNLPEntity turNLPEntity = this.getEntity(variation);
				if (turNLPEntity != null) {
					if (!entityResults.containsKey(turNLPEntity.getCollectionName())) {
						List<String> lstTerm = new ArrayList<>();
						lstTerm.add(variation.getName());
						entityResults.put(turNLPEntity.getCollectionName(), lstTerm);
					} else {
						entityResults.get(turNLPEntity.getCollectionName()).add(variation.getName());
					}
					mergeEntityResultsFromTo(this.getParentTerm(variation.getTurTerm()), entityResults);
					logger.debug("prevVariations, entityResults.size(): {}", entityResults.size());
				}
			}

			ArrayList<Integer> positionArr = new ArrayList<>();
			positionArr.add(turNLPWordPrev.getPosition());
			positionArr.add(turNLPWord.getPosition());

			TurNLPListKey<Integer> positions = new TurNLPListKey<>(positionArr);

			if (!matches.containsKey(positions)) {
				List<String> matchArray = new ArrayList<>();
				matchArray.add(variation.getId());
				matches.put(positions, matchArray);
			} else {
				matches.get(positions).add(variation.getId());
			}
		}
	}

	private void singleWord(TurEntityResults entityResults, TurNLPWord turNLPWord, TurTermVariation variation) {
		// Validate single word
		if (this.validateTerm(turNLPWord.getWord(), variation)) {
			logger.debug("Single Term was validaded: {}", turNLPWord.getWord());
			TurNLPEntity turNLPEntity = this.getEntity(variation);
			if (turNLPEntity != null) {
				logger.debug("turNLPEntity: {}", turNLPEntity.getName());
				if (!entityResults.containsKey(turNLPEntity.getCollectionName())) {
					logger.debug("First Item into collection for results: {}",
							turNLPEntity.getCollectionName());
					List<String> lstTerm = new ArrayList<>();
					lstTerm.add(variation.getName());
					entityResults.put(turNLPEntity.getCollectionName(), lstTerm);
				} else {
					logger.debug("Contains the collection for results: {}",
							turNLPEntity.getCollectionName());
					entityResults.get(turNLPEntity.getCollectionName()).add(variation.getName());
				}

				mergeEntityResultsFromTo(this.getParentTerm(variation.getTurTerm()), entityResults);
				logger.debug("prevVariations not null, entityResults.size(): {}", entityResults.size());
			} else {
				logger.debug("turNLPEntity is null");
			}

		} else {
			logger.debug("Single Term wasn't validaded: {}", turNLPWord.getWord());
		}
	}

	private void setMatches(TurEntityResults entityResults, TurNLPSentence turNLPSentence,
			Map<TurNLPListKey<Integer>, List<String>> matches) {
		logger.debug("Matches...");

		for (Entry<TurNLPListKey<Integer>, List<String>> pair : matches.entrySet()) {
			List<Integer> positions = pair.getKey().getList();
			List<String> ids = pair.getValue();
			this.mergeEntityResultsFromTo(this.checkTermIdBetweenPositions(turNLPSentence, positions, ids, matches),
					entityResults);
		}
	}

	private void logResults(TurEntityResults entityResults) {
		logger.debug("entityResults.size(): {}", entityResults.size());
		for (Entry<String, List<String>> entry : entityResults.entrySet()) {
			logger.debug("entityREsults entry Key: {}", entry.getKey());
			logger.debug("entityREsults entry Value: {}", entry.getValue());
		}
	}

	public TurEntityResults checkTermIdBetweenPositions(TurNLPSentence turNLPSentence, List<Integer> positions,
			List<String> ids, Map<TurNLPListKey<Integer>, List<String>> matches) {
		TurEntityResults entityResults = new TurEntityResults();
		logger.debug("Current Positions: {}", positions);
		if ((positions.get(0).intValue() > 0) && !ids.isEmpty()) {
			briefMatches(turNLPSentence, positions, ids, matches, entityResults);
		} else {
			logger.debug("End. First Position or Empty Ids");
		}
		return entityResults;
	}

	private void briefMatches(TurNLPSentence turNLPSentence, List<Integer> positions, List<String> ids,
			Map<TurNLPListKey<Integer>, List<String>> matches, TurEntityResults entityResults) {
		logger.debug("Brief Matches...");
		ArrayList<Integer> positionPrevArr = new ArrayList<>();
		positionPrevArr.add(positions.get(0) - 1);
		positionPrevArr.add(positions.get(0));

		logger.debug("{} = {}", positions, ids);

		TurNLPListKey<Integer> positionsPrev = new TurNLPListKey<>(positionPrevArr);
		logger.debug("PositionPrev ... {}", positionsPrev);
		if (matches.containsKey(positionsPrev)) {
			List<String> filteredIds = new ArrayList<>();
			logger.debug("Contains PositionPrev ... {}", positionsPrev);
			for (String id : ids) {
				processIds(turNLPSentence, positions, matches, entityResults, positionsPrev, filteredIds, id);
			}
		} else {
			logger.debug("Not Contains PositionPrev ... {}", positionsPrev);
		}
	}

	private void processIds(TurNLPSentence turNLPSentence, List<Integer> positions,
			Map<TurNLPListKey<Integer>, List<String>> matches, TurEntityResults entityResults,
			TurNLPListKey<Integer> positionsPrev, List<String> filteredIds, String id) {
		if (matches.containsKey(positionsPrev) && matches.get(positionsPrev).contains(id)) {
			logger.debug("Between matches found: {} | {} : {}", positionsPrev, positions, id);
			filteredIds.add(id);

			StringBuilder wordMatched = new StringBuilder();
			wordMatched.append(this.getWordsByPosition(turNLPSentence, positions.get(0) - 1));
			wordMatched.append(" ");
			wordMatched.append(this.getWordsByPosition(turNLPSentence, positions));
			logger.debug("Compare {}: {}", wordMatched, terms.get(id).getNameLower());
			if (validateTerm(wordMatched.toString(), terms.get(id))) {
				processValidatedTerm(entityResults, id, wordMatched);

			} else {
				logger.debug("Match doesn't Found");
			}
		}
		ArrayList<Integer> positionCurrArr = new ArrayList<>();
		positionCurrArr.add(positions.get(0) - 1);
		positionCurrArr.addAll(positions);
		this.mergeEntityResultsFromTo(
				this.checkTermIdBetweenPositions(turNLPSentence, positionCurrArr, filteredIds, matches),
				entityResults);
	}

	private void processValidatedTerm(TurEntityResults entityResults, String id, StringBuilder wordMatched) {
		logger.debug("Match Found");
		String entity = this.getEntity(terms.get(id)).getName().toLowerCase();
		this.mergeEntityResultsFromTo(this.getParentTerm(terms.get(id).getTurTerm()),
				entityResults);
		if (entityResults.containsKey(entity)) {
			entityResults.get(entity).add(wordMatched.toString());
		} else {
			List<String> values = new ArrayList<>();
			values.add(wordMatched.toString());
			entityResults.put(entity, values);
		}
	}

	private TurEntityResults mergeEntityResultsFromTo(TurEntityResults from, TurEntityResults to) {
		logger.debug("mergeEntity From Size: {}", from.size());
		logger.debug("mergeEntity To Size Before: {}", to.size());
		Set<Entry<String, List<String>>> fromEntries = from.entrySet();
		for (Entry<String, List<String>> fromEntry : fromEntries) {
			if (to.containsKey(fromEntry.getKey())) {
				logger.debug("mergeEntity Contains");
				logger.debug("mergeEntity To Before: {}", to.get(fromEntry.getKey()));
				logger.debug("mergeEntity From: {}", fromEntry.getValue());
				to.get(fromEntry.getKey()).addAll(fromEntry.getValue());
				logger.debug("mergeEntity To After: {}", to.get(fromEntry.getKey()));
			} else {
				logger.debug("mergeEntity Not Contains");
				List<String> values = new ArrayList<>();
				values.addAll(fromEntry.getValue());
				to.put(fromEntry.getKey(), values);
			}
		}
		logger.debug("mergeEntity To Size After: {}", to.size());
		return to;
	}

	public TurEntityResults getParentTerm(TurTerm turTerm) {
		TurEntityResults entityResults = new TurEntityResults();
		logger.debug("getParentTerm() from {}", turTerm.getName());
		for (TurTermRelationFrom relationFrom : turTerm.getTurTermRelationFroms()) {
			logger.debug("getParentTerm() relationFrom Id: {}", relationFrom.getId());
			if (relationFrom.getRelationType() == TurNLPRelationType.BT.id()) {
				logger.debug("getParentTerm() is BT");
				for (TurTermRelationTo relationTo : relationFrom.getTurTermRelationTos()) {
					logger.debug("getParentTerm() relationTo Id {}", relationTo.getId());
					TurTerm parentTerm = relationTo.getTurTerm();
					logger.debug("Parent Term is {}", parentTerm.getName());
					String parentCollection = parentTerm.getTurNLPEntity().getCollectionName();
					if (entityResults.containsKey(parentCollection)) {
						logger.debug("Parent Term Collection: {}", parentCollection);
						logger.debug("Parent Term Collection size before: {}",
								entityResults.get(parentCollection).size());
						entityResults.get(parentCollection).add(parentTerm.getName());
						logger.debug("Parent Term Collection size after: {}",
								entityResults.get(parentCollection).size());
					} else {
						logger.debug("Parent Term Collection is new: {}", parentCollection);
						List<String> lstTerm = new ArrayList<>();
						lstTerm.add(parentTerm.getName());
						entityResults.put(parentCollection, lstTerm);
						logger.debug("Parent Term Collection size: {}", entityResults.get(parentCollection).size());
					}
					mergeEntityResultsFromTo(this.getParentTerm(relationTo.getTurTerm()), entityResults);
				}
			}
		}
		logger.debug("Parent Term not found");
		return entityResults;

	}

	public TurNLPEntity getEntity(TurTermVariation variation) {
		logger.debug("Entity is {}", variation.getTurTerm().getTurNLPEntity().getName());
		return variation.getTurTerm().getTurNLPEntity();
	}

	public String getWordsByPosition(TurNLPSentence turNLPSentence, Integer position) {
		List<Integer> positions = new ArrayList<>();
		positions.add(position);
		return this.getWordsByPosition(turNLPSentence, positions);
	}

	public String getWordsByPosition(TurNLPSentence turNLPSentence, List<Integer> positions) {
		StringBuilder words = new StringBuilder();
		List<TurNLPWord> wordsbyPosition = new ArrayList<>(turNLPSentence.getWords().values());
		for (Integer position : positions) {
			TurNLPWord turNLPWord = wordsbyPosition.get(position);
			words.append(turNLPWord.getWord() + " ");

		}
		return words.toString().trim();
	}

	public boolean validateTerm(String word, TurTermVariation variation) {
		String wordNoAccent = TurCommonsUtils.stripAccents(word);
		String wordLowerCaseNoAccent = wordNoAccent.toLowerCase();
		String wordLowerCaseWithAccent = word.toLowerCase();

		String termName = terms.get(variation.getId()).getName();
		String termNameNoAccent = TurCommonsUtils.stripAccents(termName);
		String termNameLower = terms.get(variation.getId()).getNameLower();

		logger.debug("Validating..");
		if (terms.containsKey(variation.getId())) {
			logger.debug("variation.getId()).getNameLower(): {}", termNameLower);
			logger.debug("word: {}", word);
			if (termNameLower.equals(wordLowerCaseNoAccent)) {
				logger.debug("Validate... {}: {}", word, variation.getId());
				if (variation.getRuleCase() == TurNLPTermCase.CI.id()) {
					return validateCIRuleCase(variation, wordLowerCaseWithAccent, termName);
				} else if (variation.getRuleCase() == TurNLPTermCase.CS.id()) {
					return validateCSRuleCase(word, variation, wordNoAccent, termName, termNameNoAccent);
				} else if (variation.getRuleCase() == TurNLPTermCase.UCS.id()) {
					return validateUCSRuleCase(word, variation, wordNoAccent, termName, termNameLower);
				}
			}
		}
		logger.debug("Variation none = false");
		return false;
	}

	private boolean validateUCSRuleCase(String word, TurTermVariation variation, String wordNoAccent, String termName,
			String termNameLower) {
		if (termNameLower.toUpperCase().equals(wordNoAccent)) {
			logger.debug("Variation is UCS");
			if (variation.getRuleAccent() == TurNLPTermAccent.AI.id()) {
				logger.debug("Variation is UCS and AI = true");
				return true;
			} else {
				logger.debug("Variation is UCS and AS = {}", termName.toUpperCase().equals(word));
				return termName.toUpperCase().equals(word);
			}
		} else {
			logger.debug("Variation is UCS = false");
			return false;
		}
	}

	private boolean validateCIRuleCase(TurTermVariation variation, String wordLowerCaseWithAccent, String termName) {
		logger.debug("Variation is CI");
		if (variation.getRuleAccent() == TurNLPTermAccent.AI.id()) {
			logger.debug("Variation is CI and AI = true");
			return true;
		} else {
			logger.debug("Variation is CI and AS = {}",
					termName.toLowerCase().equals(wordLowerCaseWithAccent));
			return termName.toLowerCase().equals(wordLowerCaseWithAccent);
		}
	}

	private boolean validateCSRuleCase(String word, TurTermVariation variation, String wordNoAccent, String termName,
			String termNameNoAccent) {
		if (termNameNoAccent.equals(wordNoAccent)) {
			logger.debug("Variation is CS");
			if (variation.getRuleAccent() == TurNLPTermAccent.AI.id()) {
				logger.debug("Variation is CS and AI = true");
				return true;
			} else {
				logger.debug("Variation is CS and AS = {}", termName.equals(word));
				return termName.equals(word);
			}
		} else {
			logger.debug("Variation is CS = false");
			return false;
		}
	}
}
