/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.onstartup.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletManifestResult;
import com.viglet.turing.genai.feed.TurStructuredFeedIngestService;
import com.viglet.turing.genai.feed.TurStructuredFeedIngestService.IngestResult;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.genai.TurSNKnowledgeBaseMode;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.genai.TurSNSiteGenAiRepository;
import com.viglet.turing.properties.TurStructuredFeedProperty;
import com.viglet.turing.properties.TurStructuredFeedProperty.Source;
import com.viglet.turing.sn.manifest.TurSNSiteManifestService;

/**
 * T796 / §LIV.8 (Block BF) — deterministic coverage for the startup vectorless-KB
 * provisioner: default-Lucene SE resolution, explicit SE id, create-then-ingest on
 * a missing site, and the no-op path on an existing site / disabled source.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurStructuredFeedProvisioningOnStartupTest {

    @Mock
    private TurSNSiteManifestService manifestService;
    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteGenAiRepository turSNSiteGenAiRepository;
    @Mock
    private TurSEInstanceRepository turSEInstanceRepository;
    @Mock
    private TurSEVendorRepository turSEVendorRepository;
    @Mock
    private TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    @Mock
    private TurStructuredFeedIngestService ingestService;

    private TurStructuredFeedProperty property;
    private TurStructuredFeedProvisioningOnStartup runner;

    @BeforeEach
    void setUp() {
        property = new TurStructuredFeedProperty();
        property.setEnabled(true);
        runner = new TurStructuredFeedProvisioningOnStartup(property, manifestService,
                turSNSiteRepository, turSNSiteGenAiRepository, turSEInstanceRepository,
                turSEVendorRepository, turSNSiteFieldExtRepository, ingestService);
    }

    private static Source provisionSource() {
        Source s = new Source();
        s.setId("cat");
        s.setSiteName("model-catalog");
        s.setFeedUrl("https://example.com/catalog.ndjson");
        s.setLocale("en_US");
        s.setProvision(true);
        return s;
    }

    private static VigletManifestResult okResult() {
        return new VigletManifestResult("s1", "model-catalog", true,
                List.of(), List.of(), List.of(), List.of("en_US"), "1");
    }

    @Test
    void createsSiteWithReusedLuceneSeAndIngests_whenMissingAndNoExplicitSe() {
        Source source = provisionSource(); // no seInstanceId → default Lucene
        property.setSources(List.of(source));
        TurSEInstance lucene = new TurSEInstance();
        lucene.setId("lucene-1");
        when(turSNSiteRepository.findByNameIgnoreCase("model-catalog")).thenReturn(Optional.empty());
        when(turSEInstanceRepository.findByTurSEVendor_Id("LUCENE")).thenReturn(List.of(lucene));
        when(manifestService.provision(any())).thenReturn(okResult());
        when(ingestService.ingestSource(source)).thenReturn(new IngestResult(true, 5, 0, 0, 3, null));

        runner.run(null);

        ArgumentCaptor<VigletFieldManifest> manifestCaptor = ArgumentCaptor.forClass(VigletFieldManifest.class);
        verify(manifestService).provision(manifestCaptor.capture());
        assertThat(manifestCaptor.getValue().name()).isEqualTo("model-catalog");
        assertThat(manifestCaptor.getValue().seInstanceId()).isEqualTo("lucene-1"); // reused, not created
        verify(turSEInstanceRepository, never()).save(any());
        verify(ingestService).ingestSource(source);
    }

    @Test
    void createsDefaultLuceneSeInstance_whenNonePresent() {
        Source source = provisionSource();
        property.setSources(List.of(source));
        var vendor = new com.viglet.turing.persistence.model.se.TurSEVendor();
        vendor.setId("LUCENE");
        when(turSNSiteRepository.findByNameIgnoreCase("model-catalog")).thenReturn(Optional.empty());
        when(turSEInstanceRepository.findByTurSEVendor_Id("LUCENE")).thenReturn(List.of());
        when(turSEVendorRepository.findById("LUCENE")).thenReturn(Optional.of(vendor));
        // Mimic JPA assigning an id on persist so resolveSeInstanceId returns it.
        when(turSEInstanceRepository.save(any())).thenAnswer(inv -> {
            TurSEInstance i = inv.getArgument(0);
            i.setId("lucene-new");
            return i;
        });
        when(manifestService.provision(any())).thenReturn(okResult());
        when(ingestService.ingestSource(source)).thenReturn(new IngestResult(true, 1, 0, 0, 0, null));

        runner.run(null);

        verify(turSEInstanceRepository).save(any());
        ArgumentCaptor<VigletFieldManifest> manifestCaptor = ArgumentCaptor.forClass(VigletFieldManifest.class);
        verify(manifestService).provision(manifestCaptor.capture());
        assertThat(manifestCaptor.getValue().seInstanceId()).isEqualTo("lucene-new");
    }

    @Test
    void usesExplicitSeInstanceId_whenConfigured() {
        Source source = provisionSource();
        source.setSeInstanceId("my-solr");
        property.setSources(List.of(source));
        when(turSNSiteRepository.findByNameIgnoreCase("model-catalog")).thenReturn(Optional.empty());
        when(manifestService.provision(any())).thenReturn(okResult());
        when(ingestService.ingestSource(source)).thenReturn(new IngestResult(true, 1, 0, 0, 0, null));

        runner.run(null);

        ArgumentCaptor<VigletFieldManifest> manifestCaptor = ArgumentCaptor.forClass(VigletFieldManifest.class);
        verify(manifestService).provision(manifestCaptor.capture());
        assertThat(manifestCaptor.getValue().seInstanceId()).isEqualTo("my-solr");
        verify(turSEInstanceRepository, never()).findByTurSEVendor_Id(any());
    }

    @Test
    void noOpWhenSiteExists_noProvisionNoIngest() {
        Source source = provisionSource();
        property.setSources(List.of(source));
        TurSNSite site = new TurSNSite();
        site.setId("s1");
        site.setName("model-catalog");
        when(turSNSiteRepository.findByNameIgnoreCase("model-catalog")).thenReturn(Optional.of(site));
        when(turSNSiteRepository.findByIdWithGenAi("s1")).thenReturn(Optional.of(site)); // genai null → created + mode set

        runner.run(null);

        verify(manifestService, never()).provision(any());
        verify(ingestService, never()).ingestSource(any());
        // vectorless mode still asserted on the existing site (genai was null → created)
        verify(turSNSiteGenAiRepository).save(any());
    }

    @Test
    void skipsSourceWithoutProvisionFlag() {
        Source source = provisionSource();
        source.setProvision(false);
        property.setSources(List.of(source));

        runner.run(null);

        verify(manifestService, never()).provision(any());
        verify(ingestService, never()).ingestSource(any());
    }

    // ---- T810 / §LVI.3 — render wiring -----------------------------------

    @Test
    void wiresDeclaredDefaultFieldsOnTheSite_beforeTheFirstIngest() {
        Source source = provisionSource();
        source.setSeInstanceId("my-solr");
        source.setDefaultTitleField("label");
        source.setDefaultDescriptionField("summary");
        source.setDefaultUrlField("url");
        property.setSources(List.of(source));
        TurSNSite site = existingSite();
        when(turSNSiteRepository.findByNameIgnoreCase("model-catalog"))
                .thenReturn(Optional.empty()) // create path
                .thenReturn(Optional.of(site)); // subsequent lookups
        when(turSNSiteRepository.findByIdWithGenAi("s1")).thenReturn(Optional.of(site));
        when(manifestService.provision(any())).thenReturn(okResult());
        when(ingestService.ingestSource(source)).thenReturn(new IngestResult(true, 1, 0, 0, 0, null));

        runner.run(null);

        assertThat(site.getDefaultTitleField()).isEqualTo("label");
        assertThat(site.getDefaultDescriptionField()).isEqualTo("summary");
        assertThat(site.getDefaultURLField()).isEqualTo("url");
        // undeclared columns stay null so the T808 canonical fallback applies
        assertThat(site.getDefaultTextField()).isNull();
        assertThat(site.getDefaultImageField()).isNull();
        assertThat(site.getDefaultDateField()).isNull();
        verify(turSNSiteRepository).save(site);
    }

    @Test
    void neverOverwritesADefaultFieldAnOperatorAlreadySet() {
        Source source = provisionSource();
        source.setDefaultTitleField("label");
        property.setSources(List.of(source));
        TurSNSite site = existingSite();
        site.setDefaultTitleField("headline"); // admin edit
        when(turSNSiteRepository.findByNameIgnoreCase("model-catalog"))
                .thenReturn(Optional.of(site));
        when(turSNSiteRepository.findByIdWithGenAi("s1")).thenReturn(Optional.of(site));

        runner.run(null);

        assertThat(site.getDefaultTitleField()).isEqualTo("headline");
        verify(turSNSiteRepository, never()).save(site);
    }

    @Test
    void wiresDeclaredFacetLabelsOnlyWhereTheLabelIsStillBlank() {
        Source source = provisionSource();
        source.setFacetLabels(Map.of("kind", "Model Type", "vendor", "Vendor"));
        property.setSources(List.of(source));
        TurSNSite site = existingSite();
        TurSNSiteFieldExt kind = TurSNSiteFieldExt.builder().name("kind").facet(1).build();
        TurSNSiteFieldExt vendor = TurSNSiteFieldExt.builder().name("vendor").facet(1)
                .facetName("Provider").build(); // already labelled in admin
        when(turSNSiteRepository.findByNameIgnoreCase("model-catalog"))
                .thenReturn(Optional.of(site));
        when(turSNSiteRepository.findByIdWithGenAi("s1")).thenReturn(Optional.of(site));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndName(site, "kind"))
                .thenReturn(List.of(kind));
        when(turSNSiteFieldExtRepository.findByTurSNSiteAndName(site, "vendor"))
                .thenReturn(List.of(vendor));

        runner.run(null);

        assertThat(kind.getFacetName()).isEqualTo("Model Type");
        assertThat(vendor.getFacetName()).isEqualTo("Provider");
        verify(turSNSiteFieldExtRepository).save(kind);
        verify(turSNSiteFieldExtRepository, never()).save(vendor);
    }

    @Test
    void doesNotTouchFieldsWhenNoRenderMappingIsDeclared() {
        Source source = provisionSource();
        property.setSources(List.of(source));
        TurSNSite site = existingSite();
        when(turSNSiteRepository.findByNameIgnoreCase("model-catalog"))
                .thenReturn(Optional.of(site));
        when(turSNSiteRepository.findByIdWithGenAi("s1")).thenReturn(Optional.of(site));

        runner.run(null);

        verify(turSNSiteRepository, never()).save(site);
        verify(turSNSiteFieldExtRepository, never()).findByTurSNSiteAndName(any(), any());
    }

    /**
     * An existing site already in Vectorless mode, so {@code setVectorlessMode} is a
     * no-op and the only writes a test observes come from the T810 render wiring.
     */
    private static TurSNSite existingSite() {
        TurSNSite site = new TurSNSite();
        site.setId("s1");
        site.setName("model-catalog");
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setKnowledgeBaseMode(TurSNKnowledgeBaseMode.VECTORLESS_STRUCTURED);
        site.setTurSNSiteGenAi(genAi);
        return site;
    }
}
