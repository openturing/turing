/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.sn.TurSNSiteCoreUsageDomain;
import com.viglet.turing.domain.sn.TurSNSiteLocaleDomain;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

/**
 * Unit tests for {@link TurSNSiteLocaleRepositoryAdapter}, using the
 * MapStruct-generated mapper to exercise the entity-to-domain projection.
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteLocaleRepositoryAdapterTest {

    @Mock
    private TurSNSiteLocaleRepository turSNSiteLocaleRepository;

    private TurSNSiteLocaleRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurSNSiteLocaleRepositoryAdapter(turSNSiteLocaleRepository,
                Mappers.getMapper(TurSNSiteLocaleDomainMapper.class));
    }

    @Test
    void findByCoreReturnsMappedDomainListWithSnSiteIdProjected() {
        when(turSNSiteLocaleRepository.findByCore("core1"))
                .thenReturn(List.of(buildLocale("locale-1", "site-1", Locale.US, 0),
                        buildLocale("locale-2", "site-2", Locale.of("pt", "BR"), 1)));

        List<TurSNSiteLocaleDomain> domains = adapter.findByCore("core1");

        assertThat(domains).hasSize(2);
        assertThat(domains.get(0).id()).isEqualTo("locale-1");
        assertThat(domains.get(0).snSiteId()).isEqualTo("site-1");
        assertThat(domains.get(1).snSiteId()).isEqualTo("site-2");
        assertThat(domains.get(1).language()).isEqualTo(Locale.of("pt", "BR"));
        assertThat(domains.get(1).position()).isEqualTo(1);
    }

    @Test
    void findByCoreReturnsEmptyListWhenRepositoryEmpty() {
        when(turSNSiteLocaleRepository.findByCore("missing")).thenReturn(List.of());

        assertThat(adapter.findByCore("missing")).isEmpty();
    }

    @Test
    void existsByCoreIsFalseWhenRepositoryReturnsEmpty() {
        when(turSNSiteLocaleRepository.findByCore("free")).thenReturn(List.of());

        assertThat(adapter.existsByCore("free")).isFalse();
    }

    @Test
    void existsByCoreIsTrueWhenRepositoryReturnsAtLeastOne() {
        when(turSNSiteLocaleRepository.findByCore("used"))
                .thenReturn(List.of(buildLocale("locale-1", "site-1", Locale.US, 0)));

        assertThat(adapter.existsByCore("used")).isTrue();
    }

    @Test
    void existsBySnSiteIdAndLanguageDelegatesViaStubParent() {
        when(turSNSiteLocaleRepository.existsByTurSNSiteAndLanguage(
                argThat(stub -> "site-1".equals(stub.getId())), eq(Locale.US)))
                .thenReturn(true);

        assertThat(adapter.existsBySnSiteIdAndLanguage("site-1", Locale.US)).isTrue();
    }

    @Test
    void existsBySnSiteIdAndLanguageReturnsFalseWhenRepoSaysSo() {
        when(turSNSiteLocaleRepository.existsByTurSNSiteAndLanguage(
                argThat(stub -> "site-1".equals(stub.getId())), eq(Locale.GERMAN)))
                .thenReturn(false);

        assertThat(adapter.existsBySnSiteIdAndLanguage("site-1", Locale.GERMAN)).isFalse();
    }

    @Test
    void findCoreUsageDenormalisesParentSiteIdAndName() {
        TurSNSiteLocale en = buildLocale("locale-1", "site-1", Locale.US, 0);
        en.getTurSNSite().setName("MarketingSite");
        TurSNSiteLocale ptBr = buildLocale("locale-2", "site-2", Locale.of("pt", "BR"), 1);
        ptBr.getTurSNSite().setName("BlogPT");
        when(turSNSiteLocaleRepository.findByCore("core1")).thenReturn(List.of(en, ptBr));

        List<TurSNSiteCoreUsageDomain> usage = adapter.findCoreUsage("core1");

        assertThat(usage).hasSize(2);
        assertThat(usage.get(0).localeId()).isEqualTo("locale-1");
        assertThat(usage.get(0).language()).isEqualTo(Locale.US);
        assertThat(usage.get(0).snSiteId()).isEqualTo("site-1");
        assertThat(usage.get(0).snSiteName()).isEqualTo("MarketingSite");
        assertThat(usage.get(1).snSiteId()).isEqualTo("site-2");
        assertThat(usage.get(1).snSiteName()).isEqualTo("BlogPT");
        assertThat(usage.get(1).language()).isEqualTo(Locale.of("pt", "BR"));
    }

    @Test
    void findCoreUsageEmptyWhenNoLocalesBound() {
        when(turSNSiteLocaleRepository.findByCore("free")).thenReturn(List.of());

        assertThat(adapter.findCoreUsage("free")).isEmpty();
    }

    @Test
    void findBySnSiteIdOrderedByPositionDelegatesViaStubParent() {
        when(turSNSiteLocaleRepository.findByTurSNSiteOrderByPositionAsc(
                argThat(stub -> "site-1".equals(stub.getId()))))
                .thenReturn(List.of(
                        buildLocale("locale-1", "site-1", Locale.US, 0),
                        buildLocale("locale-2", "site-1", Locale.of("pt", "BR"), 1)));

        List<TurSNSiteLocaleDomain> domains =
                adapter.findBySnSiteIdOrderedByPosition("site-1");

        assertThat(domains).hasSize(2);
        assertThat(domains.get(0).id()).isEqualTo("locale-1");
        assertThat(domains.get(0).position()).isZero();
        assertThat(domains.get(0).language()).isEqualTo(Locale.US);
        assertThat(domains.get(1).id()).isEqualTo("locale-2");
        assertThat(domains.get(1).position()).isEqualTo(1);
        assertThat(domains.get(1).language()).isEqualTo(Locale.of("pt", "BR"));
    }

    @Test
    void findBySnSiteIdOrderedByPositionEmptyWhenNoLocales() {
        when(turSNSiteLocaleRepository.findByTurSNSiteOrderByPositionAsc(
                argThat(stub -> "no-such-site".equals(stub.getId()))))
                .thenReturn(List.of());

        assertThat(adapter.findBySnSiteIdOrderedByPosition("no-such-site")).isEmpty();
    }

    @Test
    void findFirstLocaleBySnSiteIdOrderByPositionReturnsMappedDomain() {
        when(turSNSiteLocaleRepository.findFirstByTurSNSiteOrderByPositionAsc(
                argThat(stub -> "site-1".equals(stub.getId()))))
                .thenReturn(buildLocale("locale-1", "site-1", Locale.US, 0));

        Optional<TurSNSiteLocaleDomain> first =
                adapter.findFirstLocaleBySnSiteIdOrderByPosition("site-1");

        assertThat(first).isPresent();
        assertThat(first.get().id()).isEqualTo("locale-1");
        assertThat(first.get().language()).isEqualTo(Locale.US);
        assertThat(first.get().snSiteId()).isEqualTo("site-1");
    }

    @Test
    void findFirstLocaleBySnSiteIdOrderByPositionEmptyWhenJpaReturnsNull() {
        // The JPA method's signature returns the entity directly (nullable) —
        // not an Optional. The adapter wraps the nullable result so callers
        // see Optional.empty() instead of NPE-prone null.
        when(turSNSiteLocaleRepository.findFirstByTurSNSiteOrderByPositionAsc(
                argThat(stub -> "no-such-site".equals(stub.getId()))))
                .thenReturn(null);

        assertThat(adapter.findFirstLocaleBySnSiteIdOrderByPosition("no-such-site")).isEmpty();
    }

    private static TurSNSiteLocale buildLocale(String id, String snSiteId, Locale language,
            int position) {
        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setId(id);
        locale.setLanguage(language);
        locale.setCore("core1");
        locale.setPosition(position);
        TurSNSite site = new TurSNSite();
        site.setId(snSiteId);
        locale.setTurSNSite(site);
        return locale;
    }
}
