package com.viglet.turing.sn.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;

class TurSNManifestHeuristicsTest {

    private static TurSNManifestFieldObservation obs(String name, Set<TurSEFieldType> types,
            int present, int total, boolean multi, int distinct, int maxLen) {
        return new TurSNManifestFieldObservation(name, present, total, types, multi, distinct, maxLen,
                List.of());
    }

    @Test
    void mandatoryWhenPresentInEveryDocument() {
        TurSNJobAttributeSpec spec = TurSNManifestHeuristics.toFieldSpec(
                obs("name", EnumSet.of(TurSEFieldType.STRING), 100, 100, false, 100, 12));
        assertTrue(spec.isMandatory());

        TurSNJobAttributeSpec optional = TurSNManifestHeuristics.toFieldSpec(
                obs("subtitle", EnumSet.of(TurSEFieldType.STRING), 40, 100, false, 40, 12));
        assertFalse(optional.isMandatory());
    }

    @Test
    void lowCardinalityStringIsFacetButFreeStringIsNot() {
        TurSNJobAttributeSpec facet = TurSNManifestHeuristics.toFieldSpec(
                obs("modalidade", EnumSet.of(TurSEFieldType.STRING), 100, 100, false, 3, 10));
        assertTrue(facet.isFacet());

        TurSNJobAttributeSpec free = TurSNManifestHeuristics.toFieldSpec(
                obs("title", EnumSet.of(TurSEFieldType.STRING), 100, 100, false, 100, 40));
        assertFalse(free.isFacet());
    }

    @Test
    void textIsNeverAFacet() {
        TurSNJobAttributeSpec spec = TurSNManifestHeuristics.toFieldSpec(
                obs("summary", EnumSet.of(TurSEFieldType.TEXT), 100, 100, false, 5, 500));
        assertEquals(TurSEFieldType.TEXT, spec.getType());
        assertFalse(spec.isFacet());
    }

    @Test
    void numericAndDateAndBoolAreFacets() {
        assertTrue(TurSNManifestHeuristics.toFieldSpec(
                obs("price", EnumSet.of(TurSEFieldType.DOUBLE), 100, 100, false, 90, 6)).isFacet());
        assertTrue(TurSNManifestHeuristics.toFieldSpec(
                obs("startsOn", EnumSet.of(TurSEFieldType.DATE), 100, 100, false, 30, 10)).isFacet());
        assertTrue(TurSNManifestHeuristics.toFieldSpec(
                obs("online", EnumSet.of(TurSEFieldType.BOOL), 100, 100, false, 2, 5)).isFacet());
    }

    @Test
    void widensMixedNumericToDouble() {
        TurSNJobAttributeSpec spec = TurSNManifestHeuristics.toFieldSpec(
                obs("amount", EnumSet.of(TurSEFieldType.INT, TurSEFieldType.DOUBLE), 100, 100, false, 90, 6));
        assertEquals(TurSEFieldType.DOUBLE, spec.getType());
    }

    @Test
    void mixedStringAndNumberFallsBackToStringOrText() {
        TurSNJobAttributeSpec shortMix = TurSNManifestHeuristics.toFieldSpec(
                obs("code", EnumSet.of(TurSEFieldType.INT, TurSEFieldType.STRING), 100, 100, false, 50, 8));
        assertEquals(TurSEFieldType.STRING, shortMix.getType());

        TurSNJobAttributeSpec longMix = TurSNManifestHeuristics.toFieldSpec(
                obs("blob", EnumSet.of(TurSEFieldType.INT, TurSEFieldType.STRING), 100, 100, false, 50,
                        TurSNManifestSampleAnalyzer.TEXT_LENGTH_THRESHOLD + 5));
        assertEquals(TurSEFieldType.TEXT, longMix.getType());
    }

    @Test
    void carriesMultiValuedFlagThrough() {
        TurSNJobAttributeSpec spec = TurSNManifestHeuristics.toFieldSpec(
                obs("tags", EnumSet.of(TurSEFieldType.STRING), 100, 100, true, 12, 6));
        assertTrue(spec.isMultiValued());
    }

    @Test
    void mapsEveryObservationPreservingOrder() {
        List<TurSNJobAttributeSpec> specs = TurSNManifestHeuristics.toFieldSpecs(List.of(
                obs("a", EnumSet.of(TurSEFieldType.STRING), 1, 1, false, 1, 1),
                obs("b", EnumSet.of(TurSEFieldType.INT), 1, 1, false, 1, 1)));
        assertEquals(List.of("a", "b"), specs.stream().map(TurSNJobAttributeSpec::getName).toList());
    }
}
