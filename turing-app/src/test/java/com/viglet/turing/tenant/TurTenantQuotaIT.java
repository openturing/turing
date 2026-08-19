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

import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.turing.api.exception.TurQuotaExhaustedException;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * T982 / §XCI — the plan limit, against a real Hibernate session.
 *
 * <p>Every other quota test mocks {@code TurAIAgentRepository.count()}, which is
 * the one thing worth doubting. `TurVigletResourceCounter` claims those counts
 * "come from the `@TenantId`-filtered repositories (so `count()` is already
 * scoped to the current tenant)", and the whole limit rests on it: if the
 * discriminator does not reach `count()`, a tenant is refused its first agent
 * because a different tenant has three — a cross-tenant leak wearing the costume
 * of a billing decision.
 *
 * <p>So the load-bearing assertion here is not that tenant A is refused a fourth
 * agent. It is that <b>tenant B is admitted its first while A is full</b>: the
 * first would pass even with a global count, and only the second can tell the
 * two apart.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurTenantQuotaIT extends AbstractTuringSpringIT {

    /** The FREE plan allows three agents (TurVigletResourceCounter). */
    private static final int FREE_AGENTS = 3;

    private static final String TENANT_FULL = "quotaFull";
    private static final String TENANT_EMPTY = "quotaEmpty";

    @DynamicPropertySource
    static void enableTenancy(DynamicPropertyRegistry registry) {
        registry.add("turing.tenancy.enabled", () -> "true");
    }

    @PersistenceContext
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private TurTenantContext tenantContext;
    @Autowired
    private TurTenantQuotaService quotaService;

    private TransactionTemplate tx;

    /**
     * Idempotent per entity, because the H2 file survives between runs and
     * `@BeforeEach` runs per method: topping up blindly made the third test see
     * nine agents where it expected three, and the gate was right while the
     * fixture was not.
     */
    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        freeTenant(TENANT_FULL);
        freeTenant(TENANT_EMPTY);
        clearAgents(TENANT_FULL);
        clearAgents(TENANT_EMPTY);
        for (int i = 0; i < FREE_AGENTS; i++) {
            createAgent(TENANT_FULL);
        }
    }

    /** The obvious half: a tenant at its limit is refused, with the T959 body. */
    @Test
    void aTenantAtItsLimitIsRefused() {
        inTenant(TENANT_FULL, () -> {
            assertThatThrownBy(quotaService::checkCanCreateAgent)
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories
                            .type(TurQuotaExhaustedException.class))
                    .satisfies(ex -> {
                        assertThat(ex.getResource()).isEqualTo(TurVigletResourceCounter.AGENTS);
                        assertThat(ex.getUsed()).isEqualTo((long) FREE_AGENTS);
                        assertThat(ex.getLimit()).isEqualTo((long) FREE_AGENTS);
                    });
            return null;
        });
    }

    /**
     * The half that matters. A global count would refuse this too, and that is
     * the bug this test exists to catch.
     */
    @Test
    void anotherTenantIsUnaffectedByTheFirstOnesUsage() {
        inTenant(TENANT_EMPTY, () -> {
            assertThatCode(quotaService::checkCanCreateAgent).doesNotThrowAnyException();
            return null;
        });
    }

    /** The counter reads the bound tenant, not the sum of every tenant. */
    @Test
    void theCountIsPerTenant() {
        long full = inTenant(TENANT_FULL,
                () -> quotaService.currentLimits().limit(TurVigletResourceCounter.AGENTS));
        assertThat(full).isEqualTo(FREE_AGENTS);

        assertThat(agentCount(TENANT_FULL)).isEqualTo(FREE_AGENTS);
        assertThat(agentCount(TENANT_EMPTY)).isZero();
    }

    /** T981's batch form rides the same counter, so it is scoped or it is not. */
    @Test
    void theBatchGateIsPerTenantToo() {
        inTenant(TENANT_EMPTY, () -> {
            assertThatCode(() -> quotaService.checkCanCreateAgents(FREE_AGENTS))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> quotaService.checkCanCreateAgents(FREE_AGENTS + 1))
                    .isInstanceOf(TurQuotaExhaustedException.class);
            return null;
        });
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    /** A tenant row on the bounded plan; without it the limits resolve UNLIMITED. */
    private void freeTenant(String id) {
        inTenant(TurTenant.DEFAULT_TENANT_ID, () -> {
            if (entityManager.find(TurTenant.class, id) == null) {
                TurTenant tenant = new TurTenant();
                tenant.setId(id);
                tenant.setSlug(id.toLowerCase());
                tenant.setName(id);
                tenant.setPlan("FREE");
                entityManager.persist(tenant);
            }
            return null;
        });
    }

    private void createAgent(String tenant) {
        inTenant(tenant, () -> {
            TurAIAgent agent = new TurAIAgent();
            agent.setId(UUID.randomUUID().toString());
            agent.setTitle("agent-" + UUID.randomUUID());
            agent.setEnabled(1);
            entityManager.persist(agent);
            return null;
        });
    }

    /** Leaves the tenant with no agents, whatever a previous run left behind. */
    private void clearAgents(String tenant) {
        inTenant(tenant, () -> entityManager
                .createQuery("delete from TurAIAgent").executeUpdate());
    }

    private long agentCount(String tenant) {
        return inTenant(tenant, () -> entityManager
                .createQuery("select count(a) from TurAIAgent a", Long.class)
                .getSingleResult());
    }

    /** Bind {@code tenant} then open a fresh transaction so Hibernate resolves it. */
    private <T> T inTenant(String tenant, Supplier<T> work) {
        return tenantContext.runAs(tenant, () -> tx.execute(status -> work.get()));
    }
}
