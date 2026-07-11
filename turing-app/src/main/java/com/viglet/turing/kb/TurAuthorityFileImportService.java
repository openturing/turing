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

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.viglet.turing.persistence.dto.kb.TurMicrothesaurusDto;
import com.viglet.turing.persistence.model.kb.TurKnowledgeBase;
import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurThesaurusRelationDirectionality;
import com.viglet.turing.persistence.model.kb.TurThesaurusRelationType;
import com.viglet.turing.persistence.model.kb.TurThesaurusTerm;
import com.viglet.turing.persistence.model.kb.TurThesaurusTermRelation;
import com.viglet.turing.persistence.model.kb.TurThesaurusTermVariation;
import com.viglet.turing.persistence.repository.kb.TurMicrothesaurusRepository;
import com.viglet.turing.sn.kb.TurMicrothesaurusDictionaryService;

/**
 * T673 / §XL (Block AQ) — imports a <em>Turing Thesaurus Exchange</em>
 * <em>authority file</em> (namespace {@code https://turing.viglet.org/xsd/thesaurus/1.0},
 * formal contract in {@code kb/turing-thesaurus-1.0.xsd}) into a
 * {@link TurMicrothesaurus} under a {@link TurKnowledgeBase}. The parser reads by
 * local element name, so it also ingests legacy controlled-vocabulary exports
 * that share the same shape regardless of their namespace.
 *
 * <p>Mapping (ADR 0003):
 * <ul>
 *   <li>{@code authorityFile/name} → microthesaurus name; the variation
 *       {@code language} (ISO 639-2/B, e.g. {@code por}) → the tree's locale.</li>
 *   <li>{@code BT} (broader) → this term's {@code parentTermId}; {@code NT}
 *       (narrower) → the target's {@code parentTermId} — the reciprocal spine
 *       collapses onto one soft parent pointer.</li>
 *   <li>{@code RT} (related) → a single {@code RELATED} bidirectional relation
 *       per unordered pair; {@code U} → {@code USE}, {@code UF} → {@code USED_FOR}
 *       directed relations.</li>
 *   <li>{@code variations} → recognition surface forms ({@code case ci}→case-
 *       insensitive, {@code accent as}→accent-sensitive).</li>
 * </ul>
 * Self-relations and duplicate (source, target, type) edges are dropped. Term ids
 * are pre-assigned UUIDs so the soft FKs (parent / relation target) resolve
 * without depending on flush ordering; the source numeric id is kept in
 * {@code externalId} for round-trip.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurAuthorityFileImportService {

    private final TurMicrothesaurusRepository microthesaurusRepository;
    private final TurMicrothesaurusDictionaryService dictionaryService;

    public TurAuthorityFileImportService(TurMicrothesaurusRepository microthesaurusRepository,
            TurMicrothesaurusDictionaryService dictionaryService) {
        this.microthesaurusRepository = microthesaurusRepository;
        this.dictionaryService = dictionaryService;
    }

    /**
     * Parses and imports {@code xml} into a new microthesaurus under {@code kb}.
     *
     * @param domainOverride optional domain; when blank, defaults to {@code GENERAL}.
     * @throws IllegalArgumentException on malformed / non-authority-file XML.
     */
    @Transactional
    public TurMicrothesaurusDto importAuthorityFile(TurKnowledgeBase kb, byte[] xml,
            String domainOverride) {
        Element root = parse(xml);
        if (!"authorityFile".equals(root.getLocalName())) {
            throw new IllegalArgumentException("Not a Turing thesaurus authority file (root <authorityFile>)");
        }
        TurMicrothesaurus microthesaurus = new TurMicrothesaurus();
        microthesaurus.setTurKnowledgeBase(kb);
        microthesaurus.setName(StringUtils.defaultIfBlank(childText(root, "name"), "Imported"));
        microthesaurus.setDomain(StringUtils.defaultIfBlank(domainOverride, "GENERAL"));
        microthesaurus.setModificationDate(LocalDateTime.now());

        List<Element> termEls = descendantTerms(root);
        Map<String, TurThesaurusTerm> bySourceId = new HashMap<>();
        List<Element> withRelations = new ArrayList<>();

        // Pass 1 — materialise every term with a stable pre-assigned id.
        Locale detectedLanguage = null;
        for (Element termEl : termEls) {
            String sourceId = childText(termEl, "id");
            if (StringUtils.isBlank(sourceId)) {
                continue;
            }
            TurThesaurusTerm term = new TurThesaurusTerm();
            term.setId(UUID.randomUUID().toString());
            term.setExternalId(sourceId);
            term.setLabel(StringUtils.defaultIfBlank(childText(termEl, "name"), sourceId));
            term.setEnabled(!"false".equalsIgnoreCase(childText(termEl, "enabled")));
            term.setTurMicrothesaurus(microthesaurus);
            Locale variationLanguage = applyVariations(term, termEl);
            if (detectedLanguage == null && variationLanguage != null) {
                detectedLanguage = variationLanguage;
            }
            bySourceId.put(sourceId, term);
            withRelations.add(termEl);
        }
        microthesaurus.setLanguage(detectedLanguage != null ? detectedLanguage : Locale.ENGLISH);

        // Pass 2 — wire the hierarchy + non-hierarchical relations.
        Set<String> relationKeys = new HashSet<>();
        for (Element termEl : withRelations) {
            TurThesaurusTerm term = bySourceId.get(childText(termEl, "id"));
            for (Element relEl : childElements(termEl, "relations", "relation")) {
                applyRelation(term, relEl, bySourceId, relationKeys);
            }
        }

        Set<TurThesaurusTerm> terms = new HashSet<>(bySourceId.values());
        microthesaurus.setTurThesaurusTerms(terms);
        TurMicrothesaurus saved = microthesaurusRepository.save(microthesaurus);
        dictionaryService.evictAll();
        return TurMicrothesaurusService.toDto(saved);
    }

    private Locale applyVariations(TurThesaurusTerm term, Element termEl) {
        List<TurThesaurusTermVariation> variations = new ArrayList<>();
        Locale firstLanguage = null;
        for (Element varEl : childElements(termEl, "variations", "variation")) {
            String surface = childText(varEl, "name");
            if (StringUtils.isBlank(surface)) {
                continue;
            }
            TurThesaurusTermVariation variation = new TurThesaurusTermVariation();
            variation.setSurfaceForm(surface.trim());
            variation.setWeight(parseDouble(childText(varEl, "weight"), 100.0d));
            variation.setCaseSensitive("cs".equalsIgnoreCase(childText(varEl, "case")));
            variation.setAccentSensitive(!"ai".equalsIgnoreCase(childText(varEl, "accent")));
            Locale language = mapIso639(firstLanguage(varEl));
            variation.setLanguage(language);
            if (firstLanguage == null) {
                firstLanguage = language;
            }
            variations.add(variation);
        }
        term.setVariations(variations);
        return firstLanguage;
    }

    private void applyRelation(TurThesaurusTerm term, Element relEl,
            Map<String, TurThesaurusTerm> bySourceId, Set<String> relationKeys) {
        String targetSourceId = childText(relEl, "id");
        String rawType = childText(relEl, "type");
        if (StringUtils.isBlank(targetSourceId) || StringUtils.isBlank(rawType)) {
            return;
        }
        TurThesaurusTerm target = bySourceId.get(targetSourceId);
        if (target == null || target == term) {
            return; // dangling or self-relation
        }
        switch (rawType.trim().toUpperCase(Locale.ROOT)) {
            case "BT" -> term.setParentTermId(target.getId());
            case "NT" -> target.setParentTermId(term.getId());
            case "RT" -> addRelation(term, target, TurThesaurusRelationType.RELATED,
                    TurThesaurusRelationDirectionality.BIDIRECTIONAL, relationKeys, true);
            case "U" -> addRelation(term, target, TurThesaurusRelationType.USE,
                    TurThesaurusRelationDirectionality.UNIDIRECTIONAL, relationKeys, false);
            case "UF" -> addRelation(term, target, TurThesaurusRelationType.USED_FOR,
                    TurThesaurusRelationDirectionality.UNIDIRECTIONAL, relationKeys, false);
            default -> addRelation(term, target, TurThesaurusRelationType.CUSTOM,
                    TurThesaurusRelationDirectionality.UNIDIRECTIONAL, relationKeys, false);
        }
    }

    private void addRelation(TurThesaurusTerm source, TurThesaurusTerm target,
            TurThesaurusRelationType type, TurThesaurusRelationDirectionality directionality,
            Set<String> relationKeys, boolean symmetric) {
        // For symmetric (RT) relations, one row per unordered pair.
        String key = symmetric
                ? type + "|" + orderedPair(source.getId(), target.getId())
                : type + "|" + source.getId() + "|" + target.getId();
        if (!relationKeys.add(key)) {
            return;
        }
        TurThesaurusTermRelation relation = new TurThesaurusTermRelation();
        relation.setId(UUID.randomUUID().toString());
        relation.setType(type);
        relation.setDirectionality(directionality);
        relation.setTargetTermId(target.getId());
        relation.setTurThesaurusTerm(source);
        source.getRelations().add(relation);
    }

    private static String orderedPair(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    // ---- DOM helpers ------------------------------------------------------

    private Element parse(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Harden against XXE — this is untrusted upload input.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml)).getDocumentElement();
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse authority file: " + e.getMessage(), e);
        }
    }

    /** Direct-child text by local name (namespace-agnostic). */
    private static String childText(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && localName.equals(node.getLocalName())) {
                return node.getTextContent();
            }
        }
        return null;
    }

    /** Elements matching {@code wrapper/child} local names, tolerant of a missing wrapper. */
    private static List<Element> childElements(Element parent, String wrapper, String child) {
        List<Element> result = new ArrayList<>();
        for (Element wrapperEl : directChildren(parent, wrapper)) {
            result.addAll(directChildren(wrapperEl, child));
        }
        return result;
    }

    private static List<Element> directChildren(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && localName.equals(node.getLocalName())) {
                result.add((Element) node);
            }
        }
        return result;
    }

    private static List<Element> descendantTerms(Element root) {
        for (Element termsEl : directChildren(root, "terms")) {
            return directChildren(termsEl, "term");
        }
        return List.of();
    }

    private static String firstLanguage(Element variationEl) {
        for (Element languagesEl : directChildren(variationEl, "languages")) {
            List<Element> langs = directChildren(languagesEl, "language");
            if (!langs.isEmpty()) {
                return langs.get(0).getTextContent();
            }
        }
        return null;
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return StringUtils.isBlank(value) ? fallback : Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Maps an ISO 639-2/B code (e.g. {@code por}) to a JVM {@link Locale} (e.g. {@code pt}). */
    static Locale mapIso639(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String trimmed = code.trim();
        if (trimmed.length() == 2) {
            return Locale.forLanguageTag(trimmed);
        }
        for (Locale available : Locale.getAvailableLocales()) {
            try {
                if (trimmed.equalsIgnoreCase(available.getISO3Language())) {
                    return Locale.forLanguageTag(available.getLanguage());
                }
            } catch (RuntimeException ignored) {
                // locale without a 3-letter code — skip
            }
        }
        return Locale.forLanguageTag(trimmed);
    }
}
