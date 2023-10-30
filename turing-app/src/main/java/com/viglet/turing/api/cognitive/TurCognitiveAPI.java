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
package com.viglet.turing.api.cognitive;

import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.language.BrazilianPortuguese;
import org.languagetool.language.BritishEnglish;
import org.languagetool.language.identifier.SimpleLanguageIdentifier;
import org.languagetool.rules.RuleMatch;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/cognitive")
@Tag(name = "Cognitive", description = "Cognitive API")

@Slf4j
public class TurCognitiveAPI {
	@Operation(summary = "Cognitive Detect Language")
	@GetMapping("/detect-language/")
	public String turCognitiveDetectLanguage(@RequestParam(required = false, name = "text") String text) {
		List<String> languages = new ArrayList<>();
		languages.add("en");
		languages.add("es");
		languages.add("pt");

		Language language = new SimpleLanguageIdentifier(languages).detectLanguage(text);
		return language != null ? language.getLocaleWithCountryAndVariant().toString() : "";
	}

	@Operation(summary = "Cognitive Spell Checker")
	@GetMapping("/spell-checker/{locale}")
	public List<String> turCognitiveSpellChecker(@PathVariable String locale,
			@RequestParam(required = false, name = "text") String text) {
		JLanguageTool langTool = loadSpellCheckByLocale(locale);
		return createSpellCheckList(text, langTool);

	}

	private JLanguageTool loadSpellCheckByLocale(String locale) {
		JLanguageTool langTool = new JLanguageTool(new BritishEnglish());
		if (locale.equalsIgnoreCase("pt-br")) {
			langTool = new JLanguageTool(new BrazilianPortuguese());
		}
		return langTool;
	}

	private List<String> createSpellCheckList(String text, JLanguageTool langTool) {
		List<RuleMatch> matches;
		try {
			matches = langTool.check(text);
			List<String> suggesterPhrases = new ArrayList<>();
			for (RuleMatch match : Lists.reverse(matches)) {
				if (suggesterPhrases.isEmpty()) {
					createListWithFirstMatch(text, suggesterPhrases, match);
				} else {
					suggesterPhrases = addToListOtherMatches(suggesterPhrases, match);
				}
				log.debug("Potential error at characters {} - {}: {}", match.getFromPos(), match.getToPos(),
						match.getMessage());
				log.debug("Suggested correction(s): {}", match.getSuggestedReplacements());
			}
			return suggesterPhrases;
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		return new ArrayList<>();
	}

	private List<String> addToListOtherMatches(List<String> suggesterPhrases, RuleMatch match) {
		List<String> suggesterPhrasesNew = new ArrayList<>();
		for (String suggesterPhrase : suggesterPhrases) {
			createListWithFirstMatch(suggesterPhrase, suggesterPhrasesNew, match);
		}
		suggesterPhrases = suggesterPhrasesNew;
		return suggesterPhrases;
	}

	private void createListWithFirstMatch(String text, List<String> suggesterPhrases, RuleMatch match) {
		for (String term : match.getSuggestedReplacements()) {
			StringBuilder buf = new StringBuilder(text);
			buf.replace(match.getFromPos(), match.getToPos(), term);
			suggesterPhrases.add(buf.toString());
		}
	}
}
