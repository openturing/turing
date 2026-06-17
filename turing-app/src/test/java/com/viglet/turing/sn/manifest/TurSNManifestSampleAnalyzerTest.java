package com.viglet.turing.sn.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;

class TurSNManifestSampleAnalyzerTest {

    private final TurSNManifestSampleAnalyzer analyzer = new TurSNManifestSampleAnalyzer();

    private static Map<String, Object> doc(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private TurSNManifestFieldObservation field(List<TurSNManifestFieldObservation> obs, String name) {
        return obs.stream().filter(o -> o.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void classifiesScalarTypesAndCoverage() {
        List<TurSNManifestFieldObservation> obs = analyzer.analyze(List.of(
                doc("name", "Algorithms", "credits", 4, "price", 1500.50, "online", true),
                doc("name", "Calculus", "credits", 6, "price", 2000.00, "online", false),
                doc("name", "Physics", "credits", 4)));

        assertEquals(3, field(obs, "name").totalDocuments());
        assertEquals(TurSEFieldType.STRING, field(obs, "name").observedTypes().iterator().next());
        assertEquals(1.0d, field(obs, "name").coverage());

        assertEquals(TurSEFieldType.INT, field(obs, "credits").observedTypes().iterator().next());
        assertEquals(TurSEFieldType.DOUBLE, field(obs, "price").observedTypes().iterator().next());
        assertEquals(TurSEFieldType.BOOL, field(obs, "online").observedTypes().iterator().next());

        // price/online present in only 2 of 3 docs -> coverage < 1
        assertEquals(2, field(obs, "price").presentCount());
    }

    @Test
    void nullIsTreatedAsAbsentNotEmpty() {
        List<TurSNManifestFieldObservation> obs = analyzer.analyze(List.of(
                doc("title", "A", "subtitle", "x"),
                doc("title", "B", "subtitle", null)));

        assertEquals(2, field(obs, "title").presentCount());
        // null lowers coverage rather than counting as a present empty value
        assertEquals(1, field(obs, "subtitle").presentCount());
        assertEquals(0.5d, field(obs, "subtitle").coverage());
    }

    @Test
    void detectsArrayAsMultiValued() {
        List<TurSNManifestFieldObservation> obs = analyzer.analyze(List.of(
                doc("tags", List.of("ai", "ml")),
                doc("tags", List.of("data"))));

        assertTrue(field(obs, "tags").multiValued());
        assertEquals(3, field(obs, "tags").distinctValueCount());
    }

    @Test
    void detectsIsoDateAndLongTextType() {
        String longText = "x".repeat(TurSNManifestSampleAnalyzer.TEXT_LENGTH_THRESHOLD + 1);
        List<TurSNManifestFieldObservation> obs = analyzer.analyze(List.of(
                doc("startsOn", "2026-03-01", "summary", longText)));

        assertEquals(TurSEFieldType.DATE, field(obs, "startsOn").observedTypes().iterator().next());
        assertEquals(TurSEFieldType.TEXT, field(obs, "summary").observedTypes().iterator().next());
    }

    @Test
    void recordsMixedTypesWhenSourceIsInconsistent() {
        List<TurSNManifestFieldObservation> obs = analyzer.analyze(List.of(
                doc("code", 100),
                doc("code", "ABC")));

        assertEquals(2, field(obs, "code").observedTypes().size());
        assertTrue(field(obs, "code").observedTypes().contains(TurSEFieldType.INT));
        assertTrue(field(obs, "code").observedTypes().contains(TurSEFieldType.STRING));
    }

    @Test
    void emptySampleYieldsNoObservations() {
        assertTrue(analyzer.analyze(List.of()).isEmpty());
        assertTrue(analyzer.analyze(null).isEmpty());
    }

    @Test
    void preservesFirstSeenFieldOrder() {
        List<TurSNManifestFieldObservation> obs = analyzer.analyze(List.of(
                doc("b", 1, "a", 2),
                doc("c", 3)));

        assertEquals(List.of("b", "a", "c"), obs.stream().map(TurSNManifestFieldObservation::name).toList());
        assertFalse(obs.isEmpty());
    }
}
