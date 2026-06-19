package com.viglet.turing.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for TurSNFieldType.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurSNFieldTypeTest {

    @Test
    void testSEFieldTypeId() {
        assertThat(TurSNFieldType.SE.id()).isEqualTo(1);
    }

    @Test
    void testNERFieldTypeId() {
        assertThat(TurSNFieldType.NER.id()).isEqualTo(2);
    }

    @Test
    void testThesaurusFieldTypeId() {
        assertThat(TurSNFieldType.THESAURUS.id()).isEqualTo(3);
    }

    @Test
    void testAllEnumValuesExist() {
        TurSNFieldType[] values = TurSNFieldType.values();

        assertThat(values)
                .hasSize(3)
                .containsExactly(TurSNFieldType.SE, TurSNFieldType.NER, TurSNFieldType.THESAURUS);
    }

    @Test
    void testValueOfSE() {
        assertThat(TurSNFieldType.valueOf("SE")).isEqualTo(TurSNFieldType.SE);
    }

    @Test
    void testValueOfNER() {
        assertThat(TurSNFieldType.valueOf("NER")).isEqualTo(TurSNFieldType.NER);
    }

    @Test
    void testValueOfTHESAURUS() {
        assertThat(TurSNFieldType.valueOf("THESAURUS")).isEqualTo(TurSNFieldType.THESAURUS);
    }

    @Test
    void testValueOfInvalidShouldThrow() {
        assertThatThrownBy(() -> TurSNFieldType.valueOf("INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIdsAreUnique() {
        assertThat(TurSNFieldType.SE.id())
                .isNotEqualTo(TurSNFieldType.NER.id())
                .isNotEqualTo(TurSNFieldType.THESAURUS.id());
        assertThat(TurSNFieldType.NER.id())
                .isNotEqualTo(TurSNFieldType.THESAURUS.id());
    }

    @Test
    void testIdsArePositive() {
        for (TurSNFieldType type : TurSNFieldType.values()) {
            assertThat(type.id()).isPositive();
        }
    }

    @Test
    void testEnumNameMatchesExpected() {
        assertThat(TurSNFieldType.SE.name()).isEqualTo("SE");
        assertThat(TurSNFieldType.NER.name()).isEqualTo("NER");
        assertThat(TurSNFieldType.THESAURUS.name()).isEqualTo("THESAURUS");
    }

    @Test
    void testOrdinalValues() {
        assertThat(TurSNFieldType.SE.ordinal()).isZero();
        assertThat(TurSNFieldType.NER.ordinal()).isEqualTo(1);
        assertThat(TurSNFieldType.THESAURUS.ordinal()).isEqualTo(2);
    }
}
