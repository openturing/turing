package com.viglet.turing.sn.ac;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class SuggestionFilter {

    private static final String SPACE_CHAR = " ";
    private final List<String> stopWords;
    // private boolean USE_BIGGER_TERMS;
    // private boolean USE_TERMS_QUERY_EQUALS_AUTO_COMPLETE;
    // private boolean USE_REPEAT_QUERY_TEXT_ON_AUTOCOMPLETE;
    private int numberOfWordsFromQuery = 0;
    private SuggestionFilterStrategy strategy;
    private boolean filterByNumberOfWordsInQuery;

    public SuggestionFilter(List<String> stopWords) {
        this.stopWords = stopWords;
    }

    public void defaultStrategyConfig(int numberOfWordsFromQuery) {
        this.strategy = SuggestionFilterStrategy.DEFAULT;
        this.numberOfWordsFromQuery = numberOfWordsFromQuery;
    }

    public void automatonStrategyConfig(int numberOfWordsFromQuery, boolean filterByNumberOfWordsInQuery) {
        this.strategy = SuggestionFilterStrategy.AUTOMATON;
        this.numberOfWordsFromQuery = numberOfWordsFromQuery;
        this.filterByNumberOfWordsInQuery = filterByNumberOfWordsInQuery;
    }

    public List<String> filter(List<String> suggestions) {
        if (suggestions == null) {
            throw new IllegalArgumentException("Suggestions list is null.");
        }

        List<String> suggestionsFiltered = new ArrayList<>();
        switch (this.strategy) {
            case DEFAULT:
                for ( String suggestion : suggestions ) {
                    if (defaultStrategy(suggestion)) {
                        suggestionsFiltered.add(suggestion);
                    }
                }
                break;
            case AUTOMATON:
                for ( String suggestion : suggestions ) {
                    if (automatonStrategy(suggestion)) {
                        suggestionsFiltered.add(suggestion);
                    }
                }
                break;
            default:
                log.warn("No strategy defined. Returning empty list.");
                return Collections.emptyList();
        }

        if (filterByNumberOfWordsInQuery) {
            try {
                suggestionsFiltered.removeIf(suggestion -> suggestion.split(SPACE_CHAR).length > numberOfWordsFromQuery);
            }
            catch (NullPointerException| UnsupportedOperationException e){
                log.error("Error filtering suggestions by number of words in query: {}", e.getMessage());
            }
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

    private boolean automatonStrategy(String suggestion) {
        // TOP -> [ "Hello", "World" ]
        Deque<String> tokenDeque = new ArrayDeque<>(List.of(suggestion.split(SPACE_CHAR)));

        // The suggestions will always include the query, so we need to ignore it.
        String originalFirstToken = tokenDeque.peek();
        int i = this.numberOfWordsFromQuery - 1;
        // Query: "Hello my friend" -> numberOfWordsFromQuery = 3
        // Query: "Hello my friend " -> numberOfWordsFromQuery = 4
        while (i > 0 && !tokenDeque.isEmpty()) {
            tokenDeque.pop();
            i--;
        }

        if (tokenDeque.isEmpty()) {
            log.warn("Suggestion is empty.");
            return false;
        }
        String currentToken = tokenDeque.pop();
        log.info("First token: {}", currentToken);
        if (stopWords.contains(originalFirstToken)) {
            // If the first token is a stop word, don't accept the suggestion
            return false;
        } else {
            if (tokenDeque.isEmpty()) {
                // If the suggestion has only one token, and it is not a stop word, accept the
                // suggestion
                return true;
            }
            currentToken = tokenDeque.pop();
            if (!stopWords.contains(currentToken)) {
                // If the second token is a not a stop word
                // and there is no more tokens, accept the suggestion
                // or if there are more tokens, don't accept the suggestion
                return tokenDeque.isEmpty();
            }
            while (!tokenDeque.isEmpty() && stopWords.contains(currentToken)) {
                // If the second token is a stop word, keep popping tokens until a non stopword
                // is found
                currentToken = tokenDeque.pop();
            }
            // If there are more tokens, don't accept the suggestion
            // If the last token is a stop word, don't accept the suggestion
            // If the last token is a word, accept the suggestion
            return tokenDeque.isEmpty() && !stopWords.contains(currentToken);
        }
    }

    private enum SuggestionFilterStrategy {
        DEFAULT,
        AUTOMATON
    }

}