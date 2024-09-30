package com.viglet.turing.sn.ac;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * @author Gabriel F. Gomazako
 * @since 0.3.9
 */
@Slf4j
public class SuggestionFilter {

    private static final String SPACE_CHAR = " ";
    private final List<String> stopWords;
    // private boolean USE_BIGGER_TERMS;
    // private boolean USE_TERMS_QUERY_EQUALS_AUTO_COMPLETE;
    // private boolean USE_REPEAT_QUERY_TEXT_ON_AUTOCOMPLETE;
    private int numberOfWordsFromQuery = 0;
    private SuggestionFilterStrategy strategy;

    public SuggestionFilter(List<String> stopWords) {
        this.stopWords = stopWords;
    }

    public void defaultStrategyConfig(int numberOfWordsFromQuery) {
        this.strategy = SuggestionFilterStrategy.DEFAULT;
        this.numberOfWordsFromQuery = numberOfWordsFromQuery;
    }

    public void automatonStrategyConfig(int numberOfWordsFromQuery) {
        this.strategy = SuggestionFilterStrategy.AUTOMATON;
        this.numberOfWordsFromQuery = numberOfWordsFromQuery;
    }

    public List<String> filter(List<String> suggestions) {
        if (suggestions == null) {
            throw new IllegalArgumentException("Suggestions list is null.");
        }

        List<String> suggestionsFiltered = new ArrayList<>();
        switch (this.strategy) {
            case DEFAULT:
                for (String suggestion : suggestions) {
                    if (defaultStrategy(suggestion)) {
                        suggestionsFiltered.add(suggestion);
                    }
                }
                break;
            case AUTOMATON:
                SuggestionAutomaton automaton = new SuggestionAutomaton();
                for (String suggestion : suggestions) {
                    if (automaton.run(suggestion, numberOfWordsFromQuery, stopWords)) {
                        suggestionsFiltered.add(suggestion);
                    }
                }
                break;
            default:
                log.warn("No strategy defined. Returning empty list.");
                return Collections.emptyList();
        }

        // log.info("====================================");
        // log.info("Original suggestions: {}", suggestions);
        // log.info("Filtered suggestions: {}", suggestionsFiltered);
        return suggestionsFiltered;
    }

    public SuggestionFilterStrategy getStrategy() {
        return this.strategy;
    }

    public void setStrategy(SuggestionFilterStrategy strategy) {
        this.strategy = strategy;
    }

    private boolean defaultStrategy(String suggestion) {
        validateDefaultStrategyConfig();

        // Example: Query: "Hello" suggestion: "Hello World" suggestion.split =
        // ["Hello", "World"].length = 2
        String[] suggestionTokens = suggestion.split(SPACE_CHAR);
        int numberOfWordsFromAutoCompleteItem = suggestionTokens.length;

        boolean numberOfWordsIsEqual = (numberOfWordsFromQuery == numberOfWordsFromAutoCompleteItem);

        boolean firstWordIsStopWord = stopWords.contains(suggestionTokens[0]);
        boolean lastWordIsStopWord = stopWords.contains(suggestionTokens[suggestionTokens.length - 1]);

        return (numberOfWordsIsEqual && !firstWordIsStopWord && !lastWordIsStopWord);
    }

    private void validateDefaultStrategyConfig() {
        if (this.stopWords == null) {
            throw new IllegalArgumentException("Stop words list is not defined.");
        }
        if (this.numberOfWordsFromQuery == 0) {
            throw new IllegalArgumentException("Number of words from query is not defined.");
        }
    }

    private enum SuggestionFilterStrategy {
        DEFAULT,
        AUTOMATON
    }

}