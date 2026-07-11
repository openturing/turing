/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.kb;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.dto.kb.TurMicrothesaurusDto;
import com.viglet.turing.persistence.dto.kb.TurThesaurusDraft;
import com.viglet.turing.persistence.dto.kb.TurThesaurusDraft.TurThesaurusDraftTerm;
import com.viglet.turing.persistence.dto.kb.TurThesaurusDraft.TurThesaurusDraftVariation;
import com.viglet.turing.persistence.dto.kb.TurThesaurusGenerationRequest;
import com.viglet.turing.persistence.model.kb.TurKnowledgeBase;

import lombok.RequiredArgsConstructor;

/**
 * T675 / §XL (Block AQ) — orchestrates LLM-assisted microthesaurus generation.
 * It asks the pluggable {@link TurThesaurusHierarchyGenerator} for a draft, then
 * <strong>sanitises</strong> it deterministically (drops dangling / self
 * {@code broader} and {@code related} references so a hallucinated edge can never
 * corrupt the tree) and returns it for review. Generation never persists.
 *
 * <p>Acceptance is a separate step: {@link #materialize(TurKnowledgeBase,
 * TurThesaurusDraft)} serialises the (possibly user-edited) draft to the T673
 * <em>authority-file</em> XML and imports it through the shipped
 * {@link TurAuthorityFileImportService} — so persistence reuses one tested
 * BT/NT/RT/variation mapping instead of a parallel path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
@RequiredArgsConstructor
public class TurThesaurusGenerationService {

    static final String NAMESPACE = "https://turing.viglet.org/xsd/thesaurus/1.0";

    private final TurThesaurusHierarchyGenerator generator;
    private final TurAuthorityFileImportService importService;

    /**
     * Generates a draft hierarchy for the request and returns the sanitised
     * proposal. Never persists.
     *
     * @throws IllegalArgumentException when domain or language is blank.
     */
    public TurThesaurusDraft generate(TurThesaurusGenerationRequest request) {
        if (request == null || StringUtils.isBlank(request.domain())
                || StringUtils.isBlank(request.language())) {
            throw new IllegalArgumentException("domain and language are required");
        }
        return sanitize(generator.generate(request));
    }

    /**
     * Persists a reviewed draft as a new microthesaurus under {@code kb} by
     * converting it to the T673 authority-file XML and importing it. The draft is
     * re-sanitised first so a hand-edited payload is still safe.
     */
    public TurMicrothesaurusDto materialize(TurKnowledgeBase kb, TurThesaurusDraft draft) {
        TurThesaurusDraft clean = sanitize(draft);
        byte[] xml = toAuthorityFileXml(clean).getBytes(StandardCharsets.UTF_8);
        return importService.importAuthorityFile(kb, xml, clean.domain());
    }

    // ---- sanitisation -----------------------------------------------------

    /**
     * Drops references that would dangle or self-loop: a {@code broader} pointing
     * at a missing id or at the term itself, and {@code related} ids that are
     * missing or self. Terms without a label are dropped entirely.
     */
    TurThesaurusDraft sanitize(TurThesaurusDraft draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft is required");
        }
        List<TurThesaurusDraftTerm> input = draft.terms() == null ? List.of() : draft.terms();
        Set<String> ids = new LinkedHashSet<>();
        input.stream()
                .filter(t -> t != null && StringUtils.isNotBlank(t.id())
                        && StringUtils.isNotBlank(t.label()))
                .forEach(t -> ids.add(t.id()));

        List<TurThesaurusDraftTerm> clean = input.stream()
                .filter(t -> t != null && ids.contains(t.id()))
                .map(t -> sanitizeTerm(t, ids))
                .toList();

        return new TurThesaurusDraft(
                StringUtils.defaultIfBlank(draft.name(), "Generated microthesaurus"),
                draft.description(),
                draft.language(),
                StringUtils.defaultIfBlank(draft.domain(), "GENERAL"),
                clean);
    }

    private TurThesaurusDraftTerm sanitizeTerm(TurThesaurusDraftTerm term, Set<String> ids) {
        String broader = StringUtils.isNotBlank(term.broader())
                && !term.broader().equals(term.id())
                && ids.contains(term.broader())
                        ? term.broader()
                        : null;
        List<String> related = (term.related() == null ? List.<String>of() : term.related()).stream()
                .filter(StringUtils::isNotBlank)
                .filter(id -> !id.equals(term.id()))
                .filter(ids::contains)
                .distinct()
                .toList();
        List<TurThesaurusDraftVariation> variations =
                (term.variations() == null ? List.<TurThesaurusDraftVariation>of() : term.variations())
                        .stream()
                        .filter(v -> v != null && StringUtils.isNotBlank(v.surfaceForm()))
                        .toList();
        return new TurThesaurusDraftTerm(term.id(), term.label().trim(),
                StringUtils.trimToNull(term.scopeNote()), broader, related, variations);
    }

    // ---- authority-file XML serialisation ---------------------------------

    /**
     * Serialises a (already-sanitised) draft into the Turing Thesaurus Exchange
     * authority-file XML the T673 importer consumes: {@code broader} → a
     * {@code BT} relation, {@code related} → {@code RT}, variations → surface
     * forms. The preferred label is always emitted as the term's first variation
     * so the importer detects the tree language and the label is recognised.
     */
    String toAuthorityFileXml(TurThesaurusDraft draft) {
        String lang = StringUtils.defaultIfBlank(draft.language(), "en");
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<authorityFile xmlns=\"").append(NAMESPACE).append("\">\n");
        sb.append("  <name>").append(escape(draft.name())).append("</name>\n");
        sb.append("  <terms>\n");
        for (TurThesaurusDraftTerm term : draft.terms()) {
            appendTerm(sb, term, lang);
        }
        sb.append("  </terms>\n");
        sb.append("</authorityFile>\n");
        return sb.toString();
    }

    private void appendTerm(StringBuilder sb, TurThesaurusDraftTerm term, String lang) {
        sb.append("    <term>\n");
        sb.append("      <id>").append(escape(term.id())).append("</id>\n");
        sb.append("      <name>").append(escape(term.label())).append("</name>\n");
        sb.append("      <enabled>true</enabled>\n");
        if (StringUtils.isNotBlank(term.scopeNote())) {
            sb.append("      <scopeNote>").append(escape(term.scopeNote())).append("</scopeNote>\n");
        }
        sb.append("      <variations>\n");
        appendVariation(sb, term.label(), false, false, lang);
        for (TurThesaurusDraftVariation v : term.variations()) {
            appendVariation(sb, v.surfaceForm(), v.caseSensitive(), v.accentSensitive(), lang);
        }
        sb.append("      </variations>\n");
        appendRelations(sb, term);
        sb.append("    </term>\n");
    }

    private void appendVariation(StringBuilder sb, String surface, boolean caseSensitive,
            boolean accentSensitive, String lang) {
        sb.append("        <variation>\n");
        sb.append("          <name>").append(escape(surface)).append("</name>\n");
        sb.append("          <weight>100.0</weight>\n");
        sb.append("          <case>").append(caseSensitive ? "cs" : "ci").append("</case>\n");
        sb.append("          <accent>").append(accentSensitive ? "as" : "ai").append("</accent>\n");
        sb.append("          <languages><language>").append(escape(lang))
                .append("</language></languages>\n");
        sb.append("        </variation>\n");
    }

    private void appendRelations(StringBuilder sb, TurThesaurusDraftTerm term) {
        boolean hasBroader = StringUtils.isNotBlank(term.broader());
        List<String> related = term.related() == null ? List.of() : term.related();
        if (!hasBroader && related.isEmpty()) {
            return;
        }
        sb.append("      <relations>\n");
        if (hasBroader) {
            appendRelation(sb, term.broader(), "BT");
        }
        for (String relatedId : related) {
            appendRelation(sb, relatedId, "RT");
        }
        sb.append("      </relations>\n");
    }

    private void appendRelation(StringBuilder sb, String targetId, String type) {
        sb.append("        <relation><id>").append(escape(targetId)).append("</id><type>")
                .append(type).append("</type></relation>\n");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
