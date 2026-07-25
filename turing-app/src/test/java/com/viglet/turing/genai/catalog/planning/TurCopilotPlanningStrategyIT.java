/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.catalog.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurCopilotPlanningStrategy;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.properties.TurCatalogCopilotProperty;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import jakarta.persistence.EntityManager;

/**
 * T818–T820 / §LIX (Block BK) — end-to-end wiring for the planning seam against the
 * real Spring context and a live schema:
 * <ul>
 *   <li>the {@code v2026.3.1.117} columns exist and round-trip through JPA — a unit
 *       test can't catch a changelog that never ran, and the factory reads them through
 *       a projection query outside any transaction;</li>
 *   <li>all three planners are registered, so
 *       {@link TurCopilotQueryPlannerFactory} can resolve every enum value instead of
 *       silently degrading a configured site back to {@code DETERMINISTIC};</li>
 *   <li>resolution precedence holds with real persistence: a site that pins nothing
 *       stays on the pre-block behaviour, and a pinned site beats the deployment
 *       default.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurCopilotPlanningStrategyIT extends AbstractTuringSpringIT {

    @Autowired
    private TurCopilotQueryPlannerFactory plannerFactory;
    @Autowired
    private TurCatalogCopilotProperty copilotProperty;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private String instanceId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        // A site needs a non-null SE instance; the vendor + instance are shared infra.
        instanceId = inTransaction(() -> {
            TurSEVendor vendor = new TurSEVendor();
            vendor.setId(UUID.randomUUID().toString().substring(0, 8));
            vendor.setTitle("BK Planning Vendor");
            entityManager.persist(vendor);

            TurSEInstance instance = new TurSEInstance();
            instance.setId(UUID.randomUUID().toString());
            instance.setTitle("BK Planning Instance");
            instance.setEndpointUrl("http://localhost:0");
            instance.setEnabled(1);
            instance.setTurSEVendor(vendor);
            entityManager.persist(instance);
            return instance.getId();
        });
    }

    @Test
    void shipsAPlannerForEveryStrategyValue() {
        TurCopilotPlanningStrategy original = copilotProperty.getPlanning().getStrategy();
        try {
            for (TurCopilotPlanningStrategy strategy : TurCopilotPlanningStrategy.values()) {
                copilotProperty.getPlanning().setStrategy(strategy);
                // An unknown site resolves purely from the deployment default, so this
                // asserts the bean registry rather than any persisted row.
                assertThat(plannerFactory.resolve("no-such-site-" + UUID.randomUUID())
                        .planner().strategy())
                        .as("a planner is registered for %s", strategy)
                        .isEqualTo(strategy);
            }
        } finally {
            copilotProperty.getPlanning().setStrategy(original);
        }
    }

    @Test
    void defaultsToDeterministicForASiteThatPinsNothing() {
        String siteName = persistSite(null, null);

        var resolution = plannerFactory.resolve(siteName);

        assertThat(resolution.planner().strategy())
                .isEqualTo(TurCopilotPlanningStrategy.DETERMINISTIC);
        assertThat(resolution.maxPasses())
                .isEqualTo(copilotProperty.getPlanning().getMaxPasses());
    }

    @Test
    void roundTripsAPinnedStrategyAndDepthThroughTheSchema() {
        String siteName = persistSite(TurCopilotPlanningStrategy.LLM_ASSISTED, 1);

        var resolution = plannerFactory.resolve(siteName);

        assertThat(resolution.planner().strategy())
                .isEqualTo(TurCopilotPlanningStrategy.LLM_ASSISTED);
        assertThat(resolution.maxPasses()).isEqualTo(1);
    }

    @Test
    void letsAPinnedSiteWinOverTheDeploymentDefault() {
        TurCopilotPlanningStrategy original = copilotProperty.getPlanning().getStrategy();
        copilotProperty.getPlanning().setStrategy(TurCopilotPlanningStrategy.LLM_ASSISTED);
        try {
            String pinned = persistSite(TurCopilotPlanningStrategy.HYBRID, null);
            String inherited = persistSite(null, null);

            assertThat(plannerFactory.resolve(pinned).planner().strategy())
                    .isEqualTo(TurCopilotPlanningStrategy.HYBRID);
            assertThat(plannerFactory.resolve(inherited).planner().strategy())
                    .isEqualTo(TurCopilotPlanningStrategy.LLM_ASSISTED);
        } finally {
            copilotProperty.getPlanning().setStrategy(original);
        }
    }

    /**
     * Persists a uniquely-named SN site whose GenAI binding carries the given planning
     * pins, and returns the site name (the key the factory resolves by). Unique per call
     * because the IT H2 file can be reused across runs, so fixtures must never collide.
     */
    private String persistSite(TurCopilotPlanningStrategy strategy, Integer maxPasses) {
        return inTransaction(() -> {
            TurSNSiteGenAi genAi = new TurSNSiteGenAi();
            genAi.setCopilotPlanningStrategy(strategy);
            genAi.setCopilotPlanningMaxPasses(maxPasses);
            entityManager.persist(genAi);

            TurSNSite site = new TurSNSite();
            String name = "bk-planning-" + UUID.randomUUID();
            site.setName(name);
            site.setTurSEInstance(entityManager.find(TurSEInstance.class, instanceId));
            site.setTurSNSiteGenAi(genAi);
            entityManager.persist(site);
            return name;
        });
    }

    private <T> T inTransaction(Supplier<T> work) {
        return tx.execute(status -> {
            T result = work.get();
            entityManager.flush();
            return result;
        });
    }
}
