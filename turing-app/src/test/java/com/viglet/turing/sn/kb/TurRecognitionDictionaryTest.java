package com.viglet.turing.sn.kb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * T671 / §XL (Block AQ) — the recognition dictionary + Aho-Corasick matcher:
 * multi-word matching in one pass, case/accent-insensitive recall by default,
 * word-boundary enforcement, per-variation flag re-verification, canonical-path
 * resolution, and de-duplication by term id.
 */
class TurRecognitionDictionaryTest {

    private static TurRecognizedTerm term(String id, String label, List<String> path) {
        return new TurRecognizedTerm(id, label, path);
    }

    @Test
    void matchesCaseAndAccentInsensitiveByDefault() {
        TurRecognizedTerm pneumonia = term("t1", "Pneumonia",
                List.of("Doença", "Doença respiratória", "Pneumonia"));
        TurRecognitionDictionary dict = TurRecognitionDictionary.builder()
                .add("pneumonia", false, false, pneumonia)
                .build();

        var found = dict.recognize("O paciente teve PNEUMÔNIA severa.");

        assertThat(found).extracting(TurRecognizedTerm::termId).containsExactly("t1");
        assertThat(found.iterator().next().path())
                .containsExactly("Doença", "Doença respiratória", "Pneumonia");
    }

    @Test
    void enforcesWordBoundaries() {
        TurRecognizedTerm cat = term("t1", "cat", List.of("cat"));
        TurRecognitionDictionary dict = TurRecognitionDictionary.builder()
                .add("cat", false, false, cat).build();

        assertThat(dict.recognize("category catalog scatter")).isEmpty();
        assertThat(dict.recognize("the cat sat")).hasSize(1);
    }

    @Test
    void matchesMultiWordSurfaceForm() {
        TurRecognizedTerm term = term("t1", "Ensino Fundamental", List.of("Educação", "Ensino Fundamental"));
        TurRecognitionDictionary dict = TurRecognitionDictionary.builder()
                .add("ensino fundamental", false, false, term).build();

        assertThat(dict.recognize("matrícula no ensino fundamental em 2026"))
                .extracting(TurRecognizedTerm::termId).containsExactly("t1");
    }

    @Test
    void accentSensitiveVariationRejectsFoldedMatch() {
        TurRecognizedTerm term = term("t1", "Doença", List.of("Doença"));
        // accent-sensitive: only the accented form counts.
        TurRecognitionDictionary dict = TurRecognitionDictionary.builder()
                .add("Doença", false, true, term).build();

        assertThat(dict.recognize("tratamento da doença hoje")).hasSize(1);
        assertThat(dict.recognize("tratamento da doenca hoje")).isEmpty();
    }

    @Test
    void deduplicatesByTermIdAcrossSurfaceForms() {
        TurRecognizedTerm term = term("t1", "Pneumonia", List.of("Pneumonia"));
        TurRecognitionDictionary dict = TurRecognitionDictionary.builder()
                .add("pneumonia", false, false, term)
                .add("pneumonite", false, false, term) // a synonym resolving to the same term
                .build();

        assertThat(dict.recognize("pneumonia e pneumonite juntas"))
                .extracting(TurRecognizedTerm::termId).containsExactly("t1");
    }

    @Test
    void emptyDictionaryRecognizesNothing() {
        TurRecognitionDictionary dict = TurRecognitionDictionary.builder().build();
        assertThat(dict.isEmpty()).isTrue();
        assertThat(dict.recognize("anything at all")).isEmpty();
    }
}
