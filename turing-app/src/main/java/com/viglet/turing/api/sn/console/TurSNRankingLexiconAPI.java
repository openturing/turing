/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.sn.console;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.sn.dsl.eval.TurNLFacetField;
import com.viglet.turing.sn.dsl.eval.TurRankingLexiconPreview;
import com.viglet.turing.sn.dsl.eval.TurRankingLexiconPreviewService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * T943 / §LVIII.8 (Block BJ) — the check an admin can run on a site's
 * <strong>ranking vocabulary</strong> before trusting it.
 *
 * <p>Ranking is fail-open by design: an unparseable bundle is ignored and the site
 * keeps the shipped vocabulary, so a typo never costs it a sort. Which is also why
 * the admin needs this — without it, the only evidence a bundle took effect is that
 * questions still rank the way they did before.
 *
 * <p>The bundle is taken from the request rather than from the stored site, so the
 * form can check a draft it has not saved. The sample question runs through the
 * real planner against the site's declared schema, which makes this a preview of
 * the behaviour rather than a second description of it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/sn/ranking-lexicon")
@Tag(name = "Semantic Navigation Ranking Lexicon",
        description = "Check a site's ranking vocabulary before saving it")
@RequiredArgsConstructor
public class TurSNRankingLexiconAPI {

    private final TurRankingLexiconPreviewService previewService;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    @Operation(summary = "Report what a ranking-lexicon bundle adds, and how a sample question ranks")
    @PostMapping("/preview")
    @Secured({"ROLE_ADMIN", "SN_ACCESS"})
    public ResponseEntity<TurRankingLexiconPreview> preview(
            @RequestBody TurRankingLexiconPreviewRequest request) {
        return ResponseEntity.ok(previewService.preview(request.locale(), request.lexicon(),
                request.query(), schemaOf(request.siteName())));
    }

    /**
     * The site's declared fields, or none when the site is unknown — a preview of a
     * bundle is still worth answering without a schema, it just cannot resolve a
     * sort, and saying so beats a 404 on the field the admin is editing.
     */
    private List<TurNLFacetField> schemaOf(String siteName) {
        return turSNSiteRepository.findByNameIgnoreCase(siteName)
                .map(this::declaredFields)
                .orElseGet(List::of);
    }

    private List<TurNLFacetField> declaredFields(TurSNSite site) {
        return turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(site, 1).stream()
                .map(TurSNRankingLexiconAPI::toField)
                .toList();
    }

    private static TurNLFacetField toField(TurSNSiteFieldExt ext) {
        TurSEFieldType type = ext.getType() != null ? ext.getType() : TurSEFieldType.TEXT;
        return new TurNLFacetField(ext.getName(), type, ext.getFacet() == 1, ext.getDescription());
    }

    /**
     * A bundle to check, the site whose declared schema to check it against, and an
     * optional sample question.
     *
     * @param siteName the SN site whose declared fields a sort may resolve to
     * @param locale   the request locale whose lexicon the bundle extends
     * @param lexicon  the bundle as the admin typed it, saved or not
     * @param query    a sample question, or blank to only report what was added
     *
     * @author Alexandre Oliveira
     * @since 2026.3.4
     */
    public record TurRankingLexiconPreviewRequest(String siteName, String locale, String lexicon,
            String query) {
    }
}
