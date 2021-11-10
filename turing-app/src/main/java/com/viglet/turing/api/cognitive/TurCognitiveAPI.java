package com.viglet.turing.api.cognitive;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.language.BritishEnglish;
import org.languagetool.language.LanguageIdentifier;
import org.languagetool.language.BrazilianPortuguese;
import org.languagetool.rules.RuleMatch;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.collect.Lists;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/cognitive")
@Tag( name = "Cognitive", description = "Cognitive API")

public class TurCognitiveAPI {
	private static final Logger logger = LogManager.getLogger(TurCognitiveAPI.class);

	@Operation(summary = "Cognitive Detect Language")
	@GetMapping("/detect-language/")
	public String turCognitiveDetectLanguage(@RequestParam(required = false, name = "text") String text) {
		LanguageIdentifier languageIdentifier = new LanguageIdentifier();
		Language language = languageIdentifier.detectLanguage(text);
		Locale locale = language.getLocaleWithCountryAndVariant();
		return locale.toString();
	}

	@Operation(summary = "Cognitive Spell Checker")
	@GetMapping("/spell-checker/{locale}")
	public List<String> turCognitiveSpellChecker(@PathVariable String locale,
			@RequestParam(required = false, name = "text") String text) {
		JLanguageTool langTool = new JLanguageTool(new BritishEnglish());
		if (locale.equalsIgnoreCase("pt-br")) {
			langTool = new JLanguageTool(new BrazilianPortuguese());
		}
		List<RuleMatch> matches;
		try {
			matches = langTool.check(text);
			List<String> suggesterPhrases = new ArrayList<>();
			for (RuleMatch match : Lists.reverse(matches)) {
				if (suggesterPhrases.isEmpty()) {
					for (String term : match.getSuggestedReplacements()) {
						StringBuilder buf = new StringBuilder(text);
						buf.replace(match.getFromPos(), match.getToPos(), term);
						suggesterPhrases.add(buf.toString());
					}
				} else {
					List<String> suggesterPhrasesNew = new ArrayList<>();
					for (String suggesterPhrase : suggesterPhrases) {
						for (String term : match.getSuggestedReplacements()) {
							StringBuilder buf = new StringBuilder(suggesterPhrase);
							buf.replace(match.getFromPos(), match.getToPos(), term);
							suggesterPhrasesNew.add(buf.toString());
						}
					}
					suggesterPhrases = suggesterPhrasesNew;
				}
				logger.debug("Potential error at characters " + match.getFromPos() + "-" + match.getToPos() + ": "
						+ match.getMessage());
				logger.debug("Suggested correction(s): " + match.getSuggestedReplacements());
			}
			return suggesterPhrases;
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		return new ArrayList<>();
	}
}
