package com.viglet.turing.sn.ac;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * The {@code SuggestionFilter} class is responsible for filtering a list of
 * suggestions
 * based on a specified strategy and a list of stop words. It currently supports
 * two
 * strategies: {@code DEFAULT} and {@code AUTOMATON}.
 * <ul>
 * <li>The {@code DEFAULT} strategy follows the original filter implemented in
 * "TurSNAutoComplete".</li>
 * <li>The {@code AUTOMATON} strategy uses a finite state machine to filter
 * suggestions. The behaviour of this strategy filters
 * suggestion for a single word, if the query has an space at the end, it will
 * consider the query as a two-word query and will suggests
 * a next word for the query. It will consider a stop word followed by a
 * non-stop as a valid suggestion.</li>
 * </ul>
 *
 * @author Gabriel F. Gomazako
 * @since 0.3.9
 */
@Slf4j
public class TurSNSuggestionFilter {

    private static final String SPACE_CHAR = " ";
    private final List<String> stopWords;
    private int numberOfWordsFromQuery = 0;
    @Setter
    @Getter
    private SuggestionFilterStrategy strategy;
    private boolean useTermsQueryEqualsAutoComplete = true;

    public TurSNSuggestionFilter(List<String> stopWords) {
        this.stopWords = stopWords;
    }

    /**
     * Configures the suggestion filter to use the default strategy - Legacy
     * strategy.
     *
     * @param numberOfWordsFromQuery the number of words from the query to be
     *                               considered in the suggestion filter.
     */
    public void defaultStrategyConfig(int numberOfWordsFromQuery) {
        this.strategy = SuggestionFilterStrategy.DEFAULT;
        this.numberOfWordsFromQuery = numberOfWordsFromQuery;
    }

    /**
     * Configures the default strategy for suggestions - Legacy strategy.
     *
     * @param numberOfWordsFromQuery          the number of words to consider from
     *                                        the query.
     * @param useTermsQueryEqualsAutoComplete flag indicating whether to use terms
     *                                        query equals auto-complete.
     */
    public void defaultStrategyConfig(int numberOfWordsFromQuery, boolean useTermsQueryEqualsAutoComplete) {
        this.defaultStrategyConfig(numberOfWordsFromQuery);
        this.useTermsQueryEqualsAutoComplete = useTermsQueryEqualsAutoComplete;
    }

    /**
     * Configures the suggestion filter to use the automaton strategy - New
     * strategy.
     *
     * @param numberOfWordsFromQuery the number of words from the query to be used
     *                               in the automaton strategy
     */
    public void automatonStrategyConfig(int numberOfWordsFromQuery) {
        this.strategy = SuggestionFilterStrategy.AUTOMATON;
        this.numberOfWordsFromQuery = numberOfWordsFromQuery;
    }

    /**
     * Filters a list of suggestions based on the defined strategy.
     *
     * @param suggestions the list of suggestions to be filtered
     * @return a list of filtered suggestions based on the strategy
     * @throws IllegalArgumentException if the suggestion list is null
     */
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
                TurSNSuggestionAutomaton automaton = new TurSNSuggestionAutomaton();
                for (String suggestion : suggestions) {
                    if (automaton.isAddSuggestion(suggestion, numberOfWordsFromQuery, stopWords)) {
                        suggestionsFiltered.add(suggestion);
                    }
                }
                break;
            default:
                log.warn("No strategy defined. Returning empty list.");
                return Collections.emptyList();
        }

        return suggestionsFiltered;
    }

    private boolean defaultStrategy(String suggestion) {
        validateDefaultStrategyConfig();

        // Example: Query: "Hello" suggestion: "Hello World" suggestion.split =
        // ["Hello", "World"].length = 2
        String[] suggestionTokens = suggestion.split(SPACE_CHAR);
        int numberOfWordsFromAutoCompleteItem = suggestionTokens.length;

        // Case: Autocompletes the current word being typed.
        // Example: Query: "Hel" suggestions = ["Hello", "Hello world", "help"] Filtered
        // suggestions = ["Hello", "help"]
        // if a query ends with space, the number of words from a query will have an extra word.
        // So it will suggest the next word.
        boolean numberOfWordsIsEqual = (numberOfWordsFromQuery == numberOfWordsFromAutoCompleteItem);

        // Case: If the first word from the suggestion is a stop word, it will not be
        // added to the list.
        // Example: Query: "The_" suggestions = ["The World", "The office"] Filtered
        // suggestions = []
        boolean firstWordIsStopWord = stopWords.contains(suggestionTokens[0]);

        // Case: If the last word from the suggestion is a stop word, it will not be
        // added to the list.
        // Example: Query: "Hello_" suggestions = ["Hello my", "Hello world"] Filtered
        // suggestions = ["Hello world"]
        boolean lastWordIsStopWord = stopWords.contains(suggestionTokens[suggestionTokens.length - 1]);

        // Disable the use of terms query equals auto complete
        // Example: Query: "Hell" suggestions = ["Hello", "Hello world", "Help"]
        // Filtered suggestions = ["Hello", "Hello world", "Help"]
        numberOfWordsIsEqual = numberOfWordsIsEqual || !this.useTermsQueryEqualsAutoComplete;

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
