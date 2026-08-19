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
 * T981 / §XC — the batch form of the plan gate, which exists because an import
 * creates many at once.
 *
 * <p>Asking the one-at-a-time gate N times would admit the first few and refuse
 * the rest, leaving a half-applied import — worse than a refused one, and the
 * failure mode T957 argued against for pairing. So the question here is always
 * "does the whole batch fit", answered before anything is written.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurTenantQuotaBatchTest {

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

    /** One agent used of three: two more fit exactly. */
    @Test
    void aBatchThatFitsIsAdmitted() {
        TurTenantQuotaService service = service(true);
        bindFreeTenant();
        when(agentRepository.count()).thenReturn(1L);

        assertThatCode(() -> service.checkCanCreateAgents(2)).doesNotThrowAnyException();
    }

    /**
     * …and three more do not. The single gate would have said yes here, because
     * one is below the limit — which is the hole this closes.
     */
    @Test
    void aBatchThatWouldCrossTheLimitIsRefusedWhole() {
        TurTenantQuotaService service = service(true);
        bindFreeTenant();
        when(agentRepository.count()).thenReturn(1L);

        assertThatThrownBy(() -> service.checkCanCreateAgents(3))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                        .type(TurQuotaExhaustedException.class))
                .satisfies(ex -> {
                    assertThat(ex.getResource()).isEqualTo(TurVigletResourceCounter.AGENTS);
                    assertThat(ex.getUsed()).isEqualTo(1L);
                    assertThat(ex.getLimit()).isEqualTo(3L);
                    // The body says what the batch would have added, not just
                    // that something is full — the operator has to know by how
                    // much to trim the archive.
                    assertThat(ex.getMessage()).contains("would add 3");
                });
    }

    @Test
    void sitesAreCountedAgainstTheirOwnLimit() {
        TurTenantQuotaService service = service(true);
        bindFreeTenant();
        when(snSiteRepository.count()).thenReturn(1L);

        assertThatCode(() -> service.checkCanCreateSites(1)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.checkCanCreateSites(2))
                .isInstanceOf(TurQuotaExhaustedException.class);
    }

    /**
     * An archive that only updates what already exists adds nothing, and must
     * not be refused on a plan it does not grow.
     */
    @Test
    void anArchiveThatAddsNothingIsNeverRefused() {
        TurTenantQuotaService service = service(true);
        bindFreeTenant();

        assertThatCode(() -> service.checkCanCreateAgents(0)).doesNotThrowAnyException();
        assertThatCode(() -> service.checkCanCreateSites(0)).doesNotThrowAnyException();
    }

    /** Single-tenant installs never count anything: the limits are unlimited. */
    @Test
    void tenancyOffAdmitsAnyBatch() {
        TurTenantQuotaService service = service(false);

        assertThatCode(() -> service.checkCanCreateAgents(1000)).doesNotThrowAnyException();
    }

    /**
     * The exemption, asserted structurally rather than trusted.
     *
     * <p>The first-boot seed imports the shipped defaults through the same
     * exchange method the HTTP endpoints use, and it runs before any tenant
     * exists — gating it would refuse a fresh install its own provisioning. What
     * keeps it exempt is that the check lives at the two controllers and the
     * seeder never sees the quota service at all.
     *
     * <p>That is exactly the kind of fact a later "we forgot to gate the seed"
     * fix would undo, so it fails here instead: no constructor parameter of
     * {@code TurExportImportOnStartup} may be the quota service.
     */
    @Test
    void theFirstBootSeedNeverSeesTheQuota() {
        for (var constructor : com.viglet.turing.onstartup.TurExportImportOnStartup.class
                .getDeclaredConstructors()) {
            assertThat(constructor.getParameterTypes())
                    .withFailMessage("The first-boot seed must stay exempt from the plan quota — "
                            + "it provisions a fresh install before any tenant exists.")
                    .doesNotContain(TurTenantQuotaService.class);
        }
    }
}
