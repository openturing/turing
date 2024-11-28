package com.viglet.turing.sn.ac;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The SuggestionAutomaton class represents a finite state machine used to validate suggestions.
 *
 * <p>Methods:
 *
 * <ul>
 *   <li>{@link #TurSNSuggestionAutomaton()}: Constructor that builds the automaton.
 *   <li>{@link #isAddSuggestion(String, int, List)}: Runs the automaton on a given suggestion,
 *       number of words from query, and stop words list.
 *   <li>{@link #getTokenType(List, String)}: Determines the token type of a given token.
 * </ul>
 *
 * @author Gabriel F. Gomazako
 * @since 0.3.9
 */
@Slf4j
public class TurSNSuggestionAutomaton {
  public static final String ACCEPT = "Accept";
  public static final String ERROR = "Error";
  public static final String FIRST_TERM_OR_STOP_WORD = "FirstTermOrStopWord";
  public static final String PREVIOUS_TERM_IS_WORD = "PreviousTermIsWord";
  private State initialState;

  public TurSNSuggestionAutomaton() {
    buildAutomaton();
  }

  /**
   * This constructs the finite state machine structure and binds to <code>initialState</code>
   * parameter.
   */
  private void buildAutomaton() {
    State firstTermOrStopWord = new State(FIRST_TERM_OR_STOP_WORD);
    State previousTermIsWord = new State(PREVIOUS_TERM_IS_WORD);
    State accept = new State(ACCEPT, State.StateType.ACCEPT);
    State reject = new State(ERROR, State.StateType.REJECT);

    setTransitions(firstTermOrStopWord, previousTermIsWord, firstTermOrStopWord, reject);
    setTransitions(previousTermIsWord, reject, reject, accept);

    this.initialState = firstTermOrStopWord;
  }

  private static void setTransitions(State state, State word, State stopWord, State empty) {
    state.transitions.put(TokenType.WORD, word);
    state.transitions.put(TokenType.STOP_WORD, stopWord);
    state.transitions.put(TokenType.EMPTY, empty);
  }

  /**
   * Runs the suggestion automaton to determine if a given suggestion is valid.
   *
   * @param suggestion the suggestion string to be evaluated.
   * @param numberOfWordsFromQuery the number of words from the current query. It will be used to
   *     know how many words the suggestion should have.
   * @param stopWords a list of stop words.
   * @return {@code true} if the suggestion is valid, according to the automaton rules, {@code
   *     false} otherwise.
   */
  public boolean isAddSuggestion(
      String suggestion, int numberOfWordsFromQuery, List<String> stopWords) {
    final Deque<String> tokensDeque =
        prepareNewSuggestionTokens(suggestion, numberOfWordsFromQuery, stopWords);
    if (tokensDeque == null || tokensDeque.isEmpty()) {
      log.debug("Suggestion is empty.");
      return false;
    }

    TokenType currentTokenType;
    State currentState = this.initialState;
    log.debug("Testing suggestion: {}", suggestion);
    String currentToken;
    while (true) {
      if (currentState.stateType == State.StateType.REJECT) {
        return false;
      } else if (currentState.stateType == State.StateType.ACCEPT) {
        return true;
      }

      currentToken = tokensDeque.poll();
      currentTokenType = getTokenType(stopWords, currentToken);

      log.debug("Current token: {} - Type: {}", currentToken, currentTokenType);
      log.debug("Current state: {}", currentState.name);

      currentState = currentState.getNextState(currentTokenType);
    }
  }

  @Nullable
  private static Deque<String> prepareNewSuggestionTokens(
      String suggestion, int numberOfWordsFromQuery, List<String> stopWords) {
    // TOP -> [ "Hello", "World" ]
    Deque<String> tokensDeque = new ArrayDeque<>(List.of(suggestion.split(" ")));

    // Suggestions should not start with a stop word when is the first term of the query.
    if (stopWords.contains(tokensDeque.peek()) && numberOfWordsFromQuery == 1) {
      return null;
    }

    // The suggestions will always include the query, so we need to ignore it.
    int wordsToRemove = numberOfWordsFromQuery - 1;
    // Query: "Hello my friend" → numberOfWordsFromQuery = 3
    // Query: "Hello my friend " → numberOfWordsFromQuery = 4
    while (wordsToRemove > 0 && !tokensDeque.isEmpty()) {
      tokensDeque.pop();
      wordsToRemove--;
    }
    return tokensDeque;
  }

  @NotNull
  private TokenType getTokenType(List<String> stopWords, String currentToken) {
    TokenType tokenType;
    if (StringUtils.isEmpty(currentToken)) {
      return TokenType.EMPTY;
    }
    if (stopWords.contains(currentToken)) {
      tokenType = TokenType.STOP_WORD;
    } else {
      tokenType = TokenType.WORD;
    }
    return tokenType;
  }

  private enum TokenType {
    WORD,
    STOP_WORD,
    EMPTY
  }

  private static class State {
    private final Map<TokenType, State> transitions = new EnumMap<>(TokenType.class);
    private final StateType stateType;
    private final String name;

    private State(String name) {
      this.stateType = StateType.NORMAL;
      this.name = name;
    }

    private State(String name, StateType stateType) {
      this.stateType = stateType;
      this.name = name;
    }

    private State getNextState(TokenType tokenType) {
      return this.transitions.get(tokenType);
    }

    private enum StateType {
      ACCEPT,
      REJECT,
      NORMAL
    }
  }
}
