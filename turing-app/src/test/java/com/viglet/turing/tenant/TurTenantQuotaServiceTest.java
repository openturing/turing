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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.tenant.TurTenantRepository;
import com.viglet.turing.properties.TurConfigProperties;

/**
 * Unit tests for {@link TurTenantQuotaService} (T277).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurTenantQuotaServiceTest {

    @Mock
    private TurTenantRepository tenantRepository;
    @Mock
    private TurAIAgentRepository agentRepository;
    @Mock
    private TurSNSiteRepository snSiteRepository;
    @Mock
    private TurLLMTokenUsageRepository tokenUsageRepository;

    private TurTenantContext context;

    private TurTenantQuotaService service(boolean tenancyEnabled) {
        TurConfigProperties props = new TurConfigProperties();
        props.getTenancy().setEnabled(tenancyEnabled);
        context = new TurTenantContext(props);
        return new TurTenantQuotaService(context, tenantRepository, agentRepository,
                snSiteRepository, tokenUsageRepository);
    }

    @AfterEach
    void clear() {
        if (context != null) {
            context.clear();
        }
    }

    private void bindFreeTenant() {
        TurTenant tenant = new TurTenant();
        tenant.setId("t-1");
        tenant.setPlan("FREE");
        lenient().when(tenantRepository.findById("t-1")).thenReturn(Optional.of(tenant));
        context.setCurrentTenant("t-1");
    }

    @Test
    void unlimitedWhenTenancyOff() {
        TurTenantQuotaService service = service(false);
        assertThat(service.currentLimits()).isEqualTo(TurTenantPlanLimits.UNLIMITED);
        assertThatCode(service::checkCanCreateAgent).doesNotThrowAnyException();
    }

    @Test
    void freePlanBlocksAgentCreationAtLimit() {
        TurTenantQuotaService service = service(true);
        bindFreeTenant();
        when(agentRepository.count()).thenReturn(3L); // FREE limit is 3

        assertThatThrownBy(service::checkCanCreateAgent)
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("402");
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
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("402");
    }

    @Test
    void paidPlanIsUnlimited() {
        TurTenant tenant = new TurTenant();
        tenant.setId("t-2");
        tenant.setPlan("PRO");
        TurTenantQuotaService service = service(true);
        when(tenantRepository.findById("t-2")).thenReturn(Optional.of(tenant));
        context.setCurrentTenant("t-2");

        assertThat(service.currentLimits()).isEqualTo(TurTenantPlanLimits.UNLIMITED);
        assertThatCode(service::checkCanCreateAgent).doesNotThrowAnyException();
    }
}
