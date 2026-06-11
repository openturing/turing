/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.viglet.turing.genai.TurRagContextBuilder;
import com.viglet.turing.genai.rag.TurRagBm25CoreProvisioner;
import com.viglet.turing.persistence.model.rag.TurRagBm25Core;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.rag.TurRagBm25CoreRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.system.TurGlobalSettingsService;

/**
 * Unit tests for {@link TurRagBm25CoreAPI}. Covers the status / provision /
 * deprovision lifecycle. The provisioner itself is mocked — its own tests
 * live in {@code TurRagBm25CoreProvisionerTest}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@ExtendWith(MockitoExtension.class)
class TurRagBm25CoreAPITest {

    private TurRagBm25CoreAPI newApi(
            TurSNSiteRepository siteRepo,
            TurSNSiteLocaleRepository localeRepo,
            TurRagBm25CoreRepository coreRepo,
            TurRagBm25CoreProvisioner provisioner,
            TurGlobalSettingsService settings,
            TurRagContextBuilder builder) {
        com.viglet.turing.tenant.TurTenantCoreNaming coreNaming =
                new com.viglet.turing.tenant.TurTenantCoreNaming(
                        new com.viglet.turing.tenant.TurTenantContext(
                                new com.viglet.turing.properties.TurConfigProperties()));
        return new TurRagBm25CoreAPI(siteRepo, localeRepo, coreRepo, provisioner, settings, builder,
                coreNaming);
    }

    private TurSNSiteLocale localeFor(String tag) {
        TurSNSiteLocale l = new TurSNSiteLocale();
        l.setLanguage(Locale.forLanguageTag(tag));
        return l;
    }

    @Test
    void listCoresReturns404WhenSiteMissing() {
        var siteRepo = mock(TurSNSiteRepository.class);
        var api = newApi(siteRepo,
                mock(TurSNSiteLocaleRepository.class),
                mock(TurRagBm25CoreRepository.class),
                mock(TurRagBm25CoreProvisioner.class),
                mock(TurGlobalSettingsService.class),
                mock(TurRagContextBuilder.class));
        when(siteRepo.findByIdNoCache("missing")).thenReturn(Optional.empty());

        ResponseEntity<List<TurRagBm25CoreAPI.CoreStatusDto>> res = api.listCores("missing");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listCoresReturnsEmptyWhenNoDefaultStore() {
        var siteRepo = mock(TurSNSiteRepository.class);
        var settings = mock(TurGlobalSettingsService.class);
        var api = newApi(siteRepo,
                mock(TurSNSiteLocaleRepository.class),
                mock(TurRagBm25CoreRepository.class),
                mock(TurRagBm25CoreProvisioner.class),
                settings,
                mock(TurRagContextBuilder.class));
        when(siteRepo.findByIdNoCache("site")).thenReturn(Optional.of(new TurSNSite()));
        when(settings.getDefaultEmbeddingStoreId()).thenReturn("");

        ResponseEntity<List<TurRagBm25CoreAPI.CoreStatusDto>> res = api.listCores("site");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isEmpty();
    }

    @Test
    void listCoresReturnsRowPerLocaleWithStatus() {
        var siteRepo = mock(TurSNSiteRepository.class);
        var localeRepo = mock(TurSNSiteLocaleRepository.class);
        var coreRepo = mock(TurRagBm25CoreRepository.class);
        var settings = mock(TurGlobalSettingsService.class);
        var builder = mock(TurRagContextBuilder.class);
        var api = newApi(siteRepo, localeRepo, coreRepo, mock(TurRagBm25CoreProvisioner.class),
                settings, builder);

        TurSNSite site = new TurSNSite();
        when(siteRepo.findByIdNoCache("site")).thenReturn(Optional.of(site));
        when(settings.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        // Locales: pt-BR (provisioned) + en-US (no row yet).
        when(localeRepo.findByTurSNSite(any(Sort.class), any())).thenReturn(List.of(
                localeFor("pt-BR"), localeFor("en-US")));

        TurStoreInstance store = new TurStoreInstance();
        store.setId("store-1-abcdef0123");
        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                null, null, null, store, "cred", "col");
        when(builder.buildFromGlobalSettings()).thenReturn(Optional.of(infra));

        TurRagBm25Core ptCore = new TurRagBm25Core();
        ptCore.setCoreName("rag_store_pt-BR");
        ptCore.setStatus(TurRagBm25Core.Status.PROVISIONED);
        ptCore.setDocCount(42L);
        when(coreRepo.findByTurStoreInstance_IdAndLocale("store-1", Locale.forLanguageTag("pt-BR")))
                .thenReturn(Optional.of(ptCore));
        when(coreRepo.findByTurStoreInstance_IdAndLocale("store-1", Locale.forLanguageTag("en-US")))
                .thenReturn(Optional.empty());

        ResponseEntity<List<TurRagBm25CoreAPI.CoreStatusDto>> res = api.listCores("site");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).hasSize(2);
        // pt-BR row: from the persisted core.
        var pt = res.getBody().get(0);
        assertThat(pt.locale()).isEqualTo("pt-BR");
        assertThat(pt.status()).isEqualTo("PROVISIONED");
        assertThat(pt.docCount()).isEqualTo(42L);
        assertThat(pt.provisioned()).isTrue();
        // en-US row: synthetic placeholder.
        var en = res.getBody().get(1);
        assertThat(en.locale()).isEqualTo("en-US");
        assertThat(en.status()).isEqualTo("NOT_PROVISIONED");
        assertThat(en.docCount()).isZero();
        assertThat(en.provisioned()).isFalse();
        assertThat(en.coreName()).contains("en-US");
    }

    @Test
    void provisionRejectsWhenSeInstanceMissing() {
        var siteRepo = mock(TurSNSiteRepository.class);
        var api = newApi(siteRepo,
                mock(TurSNSiteLocaleRepository.class),
                mock(TurRagBm25CoreRepository.class),
                mock(TurRagBm25CoreProvisioner.class),
                mock(TurGlobalSettingsService.class),
                mock(TurRagContextBuilder.class));
        TurSNSite site = new TurSNSite();
        site.setTurSNSiteGenAi(new TurSNSiteGenAi()); // no ragSeInstance
        when(siteRepo.findByIdNoCache("site")).thenReturn(Optional.of(site));

        ResponseEntity<List<TurRagBm25CoreAPI.CoreStatusDto>> res = api.provisionCores("site");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void provisionCallsProvisionerOnceLocale() {
        var siteRepo = mock(TurSNSiteRepository.class);
        var localeRepo = mock(TurSNSiteLocaleRepository.class);
        var coreRepo = mock(TurRagBm25CoreRepository.class);
        var provisioner = mock(TurRagBm25CoreProvisioner.class);
        var settings = mock(TurGlobalSettingsService.class);
        var builder = mock(TurRagContextBuilder.class);
        var api = newApi(siteRepo, localeRepo, coreRepo, provisioner, settings, builder);

        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setId("se-1");
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setRagSeInstance(seInstance);
        TurSNSite site = new TurSNSite();
        site.setTurSNSiteGenAi(genAi);

        TurStoreInstance store = new TurStoreInstance();
        store.setId("store-1-abcdef0123");
        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                null, null, null, store, "cred", "col");
        when(builder.buildFromGlobalSettings()).thenReturn(Optional.of(infra));

        when(siteRepo.findByIdNoCache("site")).thenReturn(Optional.of(site));
        when(settings.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(localeRepo.findByTurSNSite(any(Sort.class), any())).thenReturn(List.of(
                localeFor("pt-BR"), localeFor("en-US")));

        api.provisionCores("site");

        verify(provisioner, times(1)).provision(store, Locale.forLanguageTag("pt-BR"), seInstance);
        verify(provisioner, times(1)).provision(store, Locale.forLanguageTag("en-US"), seInstance);
    }

    @Test
    void provisionSwallowsPerLocaleFailures() {
        // One locale throws; the other must still be attempted. Contract:
        // partial failures are visible via the next listCores call
        // (ERROR status), not bubbled to the admin as a 500.
        var siteRepo = mock(TurSNSiteRepository.class);
        var localeRepo = mock(TurSNSiteLocaleRepository.class);
        var coreRepo = mock(TurRagBm25CoreRepository.class);
        var provisioner = mock(TurRagBm25CoreProvisioner.class);
        var settings = mock(TurGlobalSettingsService.class);
        var builder = mock(TurRagContextBuilder.class);
        var api = newApi(siteRepo, localeRepo, coreRepo, provisioner, settings, builder);

        TurSEInstance seInstance = new TurSEInstance();
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setRagSeInstance(seInstance);
        TurSNSite site = new TurSNSite();
        site.setTurSNSiteGenAi(genAi);
        when(siteRepo.findByIdNoCache("site")).thenReturn(Optional.of(site));
        when(settings.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        TurStoreInstance store = new TurStoreInstance();
        store.setId("store-1");
        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                null, null, null, store, "cred", "col");
        when(builder.buildFromGlobalSettings()).thenReturn(Optional.of(infra));
        when(localeRepo.findByTurSNSite(any(Sort.class), any())).thenReturn(List.of(
                localeFor("pt-BR"), localeFor("en-US")));
        when(provisioner.provision(any(), any(Locale.class), any()))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(new TurRagBm25Core());

        // No exception bubbles.
        ResponseEntity<List<TurRagBm25CoreAPI.CoreStatusDto>> res = api.provisionCores("site");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(provisioner, times(2)).provision(any(), any(Locale.class), any());
    }

    @Test
    void deprovisionDeletesPerLocale() {
        var siteRepo = mock(TurSNSiteRepository.class);
        var localeRepo = mock(TurSNSiteLocaleRepository.class);
        var provisioner = mock(TurRagBm25CoreProvisioner.class);
        var settings = mock(TurGlobalSettingsService.class);
        var builder = mock(TurRagContextBuilder.class);
        var api = newApi(siteRepo, localeRepo, mock(TurRagBm25CoreRepository.class),
                provisioner, settings, builder);
        when(siteRepo.findByIdNoCache("site")).thenReturn(Optional.of(new TurSNSite()));
        when(settings.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        TurStoreInstance store = new TurStoreInstance();
        store.setId("store-1");
        TurRagContextBuilder.RagInfrastructure infra = new TurRagContextBuilder.RagInfrastructure(
                null, null, null, store, "cred", "col");
        when(builder.buildFromGlobalSettings()).thenReturn(Optional.of(infra));
        when(localeRepo.findByTurSNSite(any(Sort.class), any())).thenReturn(List.of(
                localeFor("pt-BR"), localeFor("en-US")));

        api.deprovisionCores("site");

        verify(provisioner).deprovision(store, Locale.forLanguageTag("pt-BR"));
        verify(provisioner).deprovision(store, Locale.forLanguageTag("en-US"));
    }

    @Test
    void deprovisionNoOpsWhenStoreCannotResolve() {
        var siteRepo = mock(TurSNSiteRepository.class);
        var provisioner = mock(TurRagBm25CoreProvisioner.class);
        var settings = mock(TurGlobalSettingsService.class);
        var builder = mock(TurRagContextBuilder.class);
        var api = newApi(siteRepo, mock(TurSNSiteLocaleRepository.class),
                mock(TurRagBm25CoreRepository.class), provisioner, settings, builder);
        when(siteRepo.findByIdNoCache("site")).thenReturn(Optional.of(new TurSNSite()));
        when(settings.getDefaultEmbeddingStoreId()).thenReturn("store-1");
        when(builder.buildFromGlobalSettings()).thenReturn(Optional.empty());

        ResponseEntity<Void> res = api.deprovisionCores("site");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(provisioner, never()).deprovision(any(), any());
    }
}
