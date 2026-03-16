package com.viglet.turing.sn.ac;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurSNSuggestionAutomatonTest {

    private final TurSNSuggestionAutomaton automaton = new TurSNSuggestionAutomaton();

    @Test
    void shouldRejectEmptySuggestion() {
        // "" split by space -> [""] which is empty token -> EMPTY in FirstTermOrStopWord -> Error
        assertFalse(automaton.isAddSuggestion("", 1, List.of()));
    }

    @Test
    void shouldRejectSuggestionStartingWithStopWordForFirstTerm() {
        // "the book" with numberOfWordsFromQuery=1: first token "the" is stop word -> returns null -> false
        assertFalse(automaton.isAddSuggestion("the book", 1, List.of("the", "a")));
    }

    @Test
    void shouldAcceptSingleWordSuggestion() {
        // "book" with numberOfWordsFromQuery=1: no prefix removal, deque=["book"]
        // "book" -> WORD -> PreviousTermIsWord; poll -> null -> EMPTY -> Accept
        assertTrue(automaton.isAddSuggestion("book", 1, List.of("the", "a")));
    }

    @Test
    void shouldRejectWordFollowedByStopWord() {
        // deque = ["hello", "the"], no prefix removal (numberOfWordsFromQuery=1)
        // "hello" -> WORD -> PreviousTermIsWord; "the" -> STOP_WORD -> Error (REJECT)
        assertFalse(automaton.isAddSuggestion("hello the", 1, List.of("the")));
    }

    @Test
    void shouldRejectTwoConsecutiveWords() {
        // deque = ["prefix", "word"], no prefix removal
        // "prefix" -> WORD -> PreviousTermIsWord; "word" -> WORD -> Error (REJECT)
        assertFalse(automaton.isAddSuggestion("prefix word", 1, List.of()));
    }

    @Test
    void shouldAcceptSuggestionWithSingleWordAfterPrefixRemoval() {
        // "hello world" with numberOfWordsFromQuery=2: remove 1 -> deque = ["world"]
        // "world" -> WORD -> PreviousTermIsWord; EMPTY -> Accept
        assertTrue(automaton.isAddSuggestion("hello world", 2, List.of()));
    }

    @Test
    void shouldAcceptStopWordThenWordAfterPrefixRemoval() {
        // "the book" with numberOfWordsFromQuery=2: remove 1 -> deque = ["book"]
        // "book" -> WORD -> PreviousTermIsWord; EMPTY -> Accept
        assertTrue(automaton.isAddSuggestion("the book", 2, List.of("the")));
    }

    @Test
    void shouldAcceptSuggestionWithLastWordAfterLargerPrefixRemoval() {
        // "a b c" with numberOfWordsFromQuery=3: remove 2 -> deque = ["c"]
        // "c" -> WORD -> PreviousTermIsWord; EMPTY -> Accept
        assertTrue(automaton.isAddSuggestion("a b c", 3, List.of()));
    }

    @Test
    void shouldRejectWhenAllTokensRemovedByPrefix() {
        // "a b" with numberOfWordsFromQuery=3: remove 2 -> deque empty -> false
        assertFalse(automaton.isAddSuggestion("a b", 3, List.of()));
    }

    @Test
    void shouldAcceptStopWordFollowedByWordAfterPrefixSkip() {
        // "a book" with numberOfWordsFromQuery=2 and stopWords=["a"]: remove 1 -> deque = ["book"]
        // "book" -> WORD -> PreviousTermIsWord; EMPTY -> Accept
        assertTrue(automaton.isAddSuggestion("a book", 2, List.of("a")));
    }
}
