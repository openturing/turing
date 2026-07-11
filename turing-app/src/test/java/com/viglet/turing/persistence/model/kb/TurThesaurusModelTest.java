package com.viglet.turing.persistence.model.kb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * T668 / §XL (Block AQ) — pure unit tests for the Knowledge Base domain-model
 * invariants that the feature relies on: the defensive collection setters
 * (replace-not-alias, guarding against the shared-reference bug), the soft
 * {@code parentTermId} root/child semantics the index-time ancestor walk uses,
 * the variation recognition defaults, and the
 * relation type / directionality vocabulary.
 */
class TurThesaurusModelTest {

    @Test
    void knowledgeBaseSetterReplacesContentsWithoutAliasingTheArgument() {
        TurKnowledgeBase kb = new TurKnowledgeBase();
        Set<TurMicrothesaurus> original = kb.getTurMicrothesauri();

        Set<TurMicrothesaurus> incoming = Set.of(new TurMicrothesaurus());
        kb.setTurMicrothesauri(incoming);

        // Same backing collection instance is reused (clear + addAll), not aliased.
        assertThat(kb.getTurMicrothesauri()).isSameAs(original);
        assertThat(kb.getTurMicrothesauri()).hasSize(1);

        // A null argument clears rather than NPEs or replaces the instance.
        kb.setTurMicrothesauri(null);
        assertThat(kb.getTurMicrothesauri()).isSameAs(original).isEmpty();
    }

    @Test
    void knowledgeBaseDefaultsToUserSource() {
        assertThat(new TurKnowledgeBase().getSource()).isEqualTo(TurKnowledgeBaseSource.USER);
    }

    @Test
    void termVariationAndRelationSettersAreDefensive() {
        TurThesaurusTerm term = new TurThesaurusTerm();
        List<TurThesaurusTermVariation> variations = term.getVariations();
        Set<TurThesaurusTermRelation> relations = term.getRelations();

        term.setVariations(new ArrayList<>(List.of(new TurThesaurusTermVariation("pneumonia",
                Locale.forLanguageTag("pt")))));
        term.setRelations(Set.of(new TurThesaurusTermRelation()));

        assertThat(term.getVariations()).isSameAs(variations).hasSize(1);
        assertThat(term.getRelations()).isSameAs(relations).hasSize(1);

        term.setVariations(null);
        term.setRelations(null);
        assertThat(term.getVariations()).isSameAs(variations).isEmpty();
        assertThat(term.getRelations()).isSameAs(relations).isEmpty();
    }

    @Test
    void nullParentTermIdMarksARootTerm() {
        TurThesaurusTerm root = new TurThesaurusTerm();
        TurThesaurusTerm child = new TurThesaurusTerm();
        child.setParentTermId("root-id");

        assertThat(root.getParentTermId()).isNull();      // root
        assertThat(child.getParentTermId()).isEqualTo("root-id");
        assertThat(root.isEnabled()).isTrue();            // enabled by default
    }

    @Test
    void variationDefaultsMirrorTheRecognitionFlags() {
        TurThesaurusTermVariation v = new TurThesaurusTermVariation("Pólen",
                Locale.forLanguageTag("pt"));
        // Defaults: case-insensitive (ci) + accent-sensitive (as), weight 100.
        assertThat(v.isCaseSensitive()).isFalse();
        assertThat(v.isAccentSensitive()).isTrue();
        assertThat(v.getWeight()).isEqualTo(100.0d);
        assertThat(v.getSurfaceForm()).isEqualTo("Pólen");
    }

    @Test
    void relationDefaultsToUnidirectionalAndCoversTheContractTypes() {
        assertThat(new TurThesaurusTermRelation().getDirectionality())
                .isEqualTo(TurThesaurusRelationDirectionality.UNIDIRECTIONAL);

        // The full Turing Thesaurus Exchange relation vocabulary is present.
        assertThat(TurThesaurusRelationType.values()).containsExactlyInAnyOrder(
                TurThesaurusRelationType.BROADER, TurThesaurusRelationType.NARROWER,
                TurThesaurusRelationType.RELATED, TurThesaurusRelationType.USE,
                TurThesaurusRelationType.USED_FOR, TurThesaurusRelationType.CUSTOM);
    }
}
