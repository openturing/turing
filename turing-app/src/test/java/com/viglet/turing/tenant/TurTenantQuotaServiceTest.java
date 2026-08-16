/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.viglet.core.tenancy.VigletPlanLimits;
import com.viglet.core.tenancy.VigletQuotaService;
import com.viglet.core.tenancy.VigletTenantRef;
import com.viglet.core.tenancy.VigletTenantStatus;
import com.viglet.core.tenancy.VigletTenantStore;
import com.viglet.turing.api.exception.TurQuotaExhaustedException;
import com.viglet.turing.genai.vigletai.TurVigletUpgradeTargetService;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.properties.TurConfigProperties;

/**
 * Unit tests for {@link TurTenantQuotaService} — the Turing per-resource entry
 * points delegating to the shared {@link VigletQuotaService}, fed by Turing's
 * {@link TurVigletResourceCounter} (T277, re-homed in T396 / §XIV.9). The
 * compare-and-throw is the shared service's; here we assert the Turing FREE plan
 * limits (3 agents / 2 sites) and the 402 / no-op outcomes still hold.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurTenantQuotaServiceTest {

    @Mock
    private VigletTenantStore store;
    @Mock
    private TurAIAgentRepository agentRepository;
    @Mock
    private TurSNSiteRepository snSiteRepository;
    @Mock
    private TurLLMTokenUsageRepository tokenUsageRepository;
    @Mock
    private TurVigletUpgradeTargetService upgradeTargetService;

    private TurTenantContext context;

    private TurTenantQuotaService service(boolean tenancyEnabled) {
        TurConfigProperties props = new TurConfigProperties();
        props.getTenancy().setEnabled(tenancyEnabled);
        context = new TurTenantContext(props);
        TurVigletResourceCounter counter = new TurVigletResourceCounter(agentRepository,
                snSiteRepository, tokenUsageRepository);
        VigletQuotaService quota = new VigletQuotaService(context, store, counter);
        lenient().when(upgradeTargetService.upgradeUrl(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        return new TurTenantQuotaService(quota, context, counter, upgradeTargetService);
    }

    @AfterEach
    void clear() {
        if (context != null) {
            context.clear();
        }
    }

    private void bindFreeTenant() {
        VigletTenantRef ref = new VigletTenantRef("t-1", "acme", "Acme", VigletTenantStatus.ACTIVE, "FREE");
        lenient().when(store.findById("t-1")).thenReturn(Optional.of(ref));
        context.setCurrentTenant("t-1");
    }

    @Test
    void unlimitedWhenTenancyOff() {
        TurTenantQuotaService service = service(false);
        assertThat(service.currentLimits()).isEqualTo(VigletPlanLimits.UNLIMITED);
        assertThatCode(service::checkCanCreateAgent).doesNotThrowAnyException();
    }

    @Test
    void freePlanBlocksAgentCreationAtLimit() {
        TurTenantQuotaService service = service(true);
        bindFreeTenant();
        when(agentRepository.count()).thenReturn(3L); // FREE limit is 3

        assertThatThrownBy(service::checkCanCreateAgent)
                .isInstanceOf(TurQuotaExhaustedException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(TurQuotaExhaustedException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                    assertThat(ex.getResource()).isEqualTo(TurVigletResourceCounter.AGENTS);
                    assertThat(ex.getUsed()).isEqualTo(3L);
                    assertThat(ex.getLimit()).isEqualTo(3L);
                    // A resource count does not come back by waiting.
                    assertThat(ex.getResetsAt()).isNull();
                });
    }

    @Test
    void freePlanAllowsAgentCreationBelowLimit() {
        TurTenantQuotaService service = service(true);
        bindFreeTenant();
        when(agentRepository.count()).thenReturn(1L);

        assertThatCode(service::checkCanCreateAgent).doesNotThrowAnyException();
    }

    @Test
    void freePlanBlocksSiteCreationAtLimit() {
        TurTenantQuotaService service = service(true);
        bindFreeTenant();
        when(snSiteRepository.count()).thenReturn(2L); // FREE limit is 2

        assertThatThrownBy(service::checkCanCreateSite)
                .isInstanceOf(TurQuotaExhaustedException.class)
                .hasMessageContaining("2 of 2 used");
    }

    /**
     * T959 — the token budget is the one limit that comes back on its own, so it
     * is the one refusal allowed to say when. 429, not 402: the work is not
     * unaffordable, it is early.
     */
    @Test
    void monthlyTokenRefusalCarriesTheResetInstant() {
        TurTenantQuotaService service = service(true);
        bindFreeTenant();
        when(tokenUsageRepository.findUsageByTenant(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.<Object[]>of(new Object[] { "t-1", "x", "y", 100_000L }));

        assertThatThrownBy(service::checkLlmAdmission)
                .isInstanceOf(TurQuotaExhaustedException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(TurQuotaExhaustedException.class))
                .satisfies(ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getResource()).isEqualTo(TurVigletResourceCounter.MONTHLY_TOKENS);
                    assertThat(ex.getResetsAt()).isNotNull();
                    assertThat(ex.getResetsAt().getDayOfMonth()).isEqualTo(1);
                    assertThat(ex.getResetsAt()).isAfter(java.time.OffsetDateTime.now());
                    assertThat(ex.getMessage()).contains("Resets on");
                });
    }

    /**
     * T959 — an install with no shared secret still gets a usable refusal: the
     * paid exit is simply absent rather than pointing at an unsigned link.
     */
    @Test
    void refusalOmitsTheUpgradeTargetWhenNoneCanBeSigned() {
        TurTenantQuotaService service = service(true);
        bindFreeTenant();
        when(agentRepository.count()).thenReturn(3L);

        assertThatThrownBy(service::checkCanCreateAgent)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(TurQuotaExhaustedException.class))
                .satisfies(ex -> assertThat(ex.getUpgradeUrl()).isNull());
    }

    /** T959 — when a target can be signed, it travels on the refusal. */
    @Test
    void refusalCarriesTheSignedUpgradeTarget() {
        TurTenantQuotaService service = service(true);
        bindFreeTenant();
        when(agentRepository.count()).thenReturn(3L);
        when(upgradeTargetService.upgradeUrl("t-1", TurVigletResourceCounter.AGENTS))
                .thenReturn(Optional.of("https://viglet.cloud/upgrade?sig=abc"));

        assertThatThrownBy(service::checkCanCreateAgent)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(TurQuotaExhaustedException.class))
                .satisfies(ex -> assertThat(ex.getUpgradeUrl())
                        .isEqualTo("https://viglet.cloud/upgrade?sig=abc"));
    }

    @Test
    void paidPlanIsUnlimited() {
        TurTenantQuotaService service = service(true);
        VigletTenantRef ref = new VigletTenantRef("t-2", "pro", "Pro", VigletTenantStatus.ACTIVE, "PRO");
        when(store.findById("t-2")).thenReturn(Optional.of(ref));
        context.setCurrentTenant("t-2");

        assertThat(service.currentLimits()).isEqualTo(VigletPlanLimits.UNLIMITED);
        assertThatCode(service::checkCanCreateAgent).doesNotThrowAnyException();
    }
}
