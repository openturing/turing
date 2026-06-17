/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.dsl.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.turing.sn.dsl.eval.TurNLFacetExpectation.ExpectedFilter;
import com.viglet.turing.sn.dsl.eval.TurNLFacetExpectation.ExpectedRange;
import com.viglet.turing.sn.manifest.TurSNManifestMapper;

/**
 * Auto-generates a candidate {@link TurNLFacetEvalPack} from a T382
 * {@link VigletFieldManifest} (T385 / §XX.5): declared fields become candidate
 * filter/range eval cases, so onboarding a new catalog starts from a generated
 * eval pack instead of hand-writing every case.
 *
 * <p>Grounding values cannot be invented from the schema alone, so the caller
 * supplies them: a {@code facetSamples} map ({@code field -> a real value}) seeds
 * facet-equality cases, and a {@code rangeThresholds} map ({@code field -> N})
 * seeds "under N" range cases. Fields without a supplied value are skipped — the
 * generator never fabricates data, matching the conservative-grounding invariant.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurNLFacetEvalGenerator {

    /**
     * Build a candidate pack from a manifest.
     *
     * @param manifest        the declared schema (T382).
     * @param facetSamples    {@code field -> sample value} for facet-equality cases.
     * @param rangeThresholds {@code field -> threshold} for "under N" range cases.
     */
    public TurNLFacetEvalPack fromManifest(VigletFieldManifest manifest,
            Map<String, String> facetSamples, Map<String, Double> rangeThresholds) {
        Map<String, String> samples = facetSamples == null ? Map.of() : facetSamples;
        Map<String, Double> thresholds = rangeThresholds == null ? Map.of() : rangeThresholds;

        List<TurNLFacetField> fields = new ArrayList<>();
        List<TurNLFacetEvalCase> cases = new ArrayList<>();

        for (VigletFieldSpec spec : safe(manifest.fields())) {
            if (spec == null || spec.name() == null) {
                continue;
            }
            TurNLFacetField field = new TurNLFacetField(
                    spec.name(), TurSNManifestMapper.toSeType(spec.type()), spec.facet(),
                    spec.description());
            fields.add(field);

            String sample = samples.get(spec.name());
            if (spec.facet() && sample != null && !sample.isBlank()) {
                cases.add(facetCase(field, sample));
            }
            Double threshold = thresholds.get(spec.name());
            if (field.numeric() && threshold != null) {
                cases.add(rangeCase(field, threshold));
            }
        }

        String name = "auto:" + (manifest.name() == null ? "manifest" : manifest.name());
        String locale = firstLocale(manifest);
        return new TurNLFacetEvalPack(name, manifest.name(), locale, fields, cases);
    }

    private TurNLFacetEvalCase facetCase(TurNLFacetField field, String value) {
        return new TurNLFacetEvalCase(
                "facet:%s=%s".formatted(field.name(), value),
                "%s %s".formatted(field.name(), value),
                new TurNLFacetExpectation(
                        List.of(new ExpectedFilter(field.name(), value)), List.of(), null));
    }

    private TurNLFacetEvalCase rangeCase(TurNLFacetField field, double threshold) {
        return new TurNLFacetEvalCase(
                "range:%s<=%s".formatted(field.name(), threshold),
                "%s under %s".formatted(field.name(), trim(threshold)),
                new TurNLFacetExpectation(List.of(),
                        List.of(new ExpectedRange(field.name(), null, null, threshold, null)), null));
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String firstLocale(VigletFieldManifest manifest) {
        List<String> locales = manifest.locales();
        return locales != null && !locales.isEmpty() ? locales.get(0) : null;
    }

    private static List<VigletFieldSpec> safe(List<VigletFieldSpec> fields) {
        return fields == null ? List.of() : fields;
    }
}
