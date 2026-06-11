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

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.tenant.TurTenant;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * T260 / §XIV.2.4 — proves the Hibernate {@code @TenantId} pilot on
 * {@link TurSNSite}: writes are auto-stamped with the current tenant and reads
 * are auto-filtered, so tenant B is blind to tenant A's sites.
 *
 * <p>Boots with {@code turing.tenancy.enabled=true}. Each tenant operation runs
 * in its own transaction (via {@link TransactionTemplate}) inside
 * {@link TurTenantContext#runAs}, because Hibernate binds the tenant identifier
 * when the session opens — switching the thread-local mid-session would not
 * re-resolve it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurSNSiteTenantIsolationIT extends AbstractTuringSpringIT {

    private static final String TENANT_A = "tenantA";
    private static final String TENANT_B = "tenantB";

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

    private TransactionTemplate tx;
    private String instanceId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        // Vendor + SE instance carry no @TenantId, so they are tenant-agnostic
        // shared infra both sites can reference.
        instanceId = inTenant(TurTenant.DEFAULT_TENANT_ID, () -> {
            TurSEVendor vendor = new TurSEVendor();
            vendor.setId(UUID.randomUUID().toString().substring(0, 8));
            vendor.setTitle("Test Vendor");
            entityManager.persist(vendor);

            TurSEInstance instance = new TurSEInstance();
            instance.setId(UUID.randomUUID().toString());
            instance.setTitle("Test Instance");
            instance.setEndpointUrl("http://localhost:0");
            instance.setEnabled(1);
            instance.setTurSEVendor(vendor);
            entityManager.persist(instance);
            return instance.getId();
        });
    }

    @Test
    void writeIsStampedAndReadIsFilteredByTenant() {
        String idA = createSite(TENANT_A, "site-A-" + UUID.randomUUID());
        String idB = createSite(TENANT_B, "site-B-" + UUID.randomUUID());

        // Hibernate stamped the discriminator from the bound tenant on insert.
        assertThat(tenantIdOf(idA)).isEqualTo(TENANT_A);
        assertThat(tenantIdOf(idB)).isEqualTo(TENANT_B);

        List<String> seenByA = listSiteIds(TENANT_A);
        assertThat(seenByA).contains(idA).doesNotContain(idB);

        List<String> seenByB = listSiteIds(TENANT_B);
        assertThat(seenByB).contains(idB).doesNotContain(idA);
    }

    @Test
    void findByIdIsTenantScoped() {
        String idA = createSite(TENANT_A, "scoped-" + UUID.randomUUID());

        TurSNSite asOwner = inTenant(TENANT_A, () -> entityManager.find(TurSNSite.class, idA));
        TurSNSite asOther = inTenant(TENANT_B, () -> entityManager.find(TurSNSite.class, idA));

        assertThat(asOwner).isNotNull();
        assertThat(asOther).isNull(); // tenant B cannot load tenant A's row by id
    }

    private String createSite(String tenant, String name) {
        return inTenant(tenant, () -> {
            TurSNSite site = new TurSNSite();
            site.setName(name);
            site.setTurSEInstance(entityManager.find(TurSEInstance.class, instanceId));
            entityManager.persist(site);
            return site.getId();
        });
    }

    private List<String> listSiteIds(String tenant) {
        return inTenant(tenant, () -> entityManager
                .createQuery("select s.id from TurSNSite s", String.class)
                .getResultList());
    }

    /** Raw discriminator value, read in system mode so no filter hides the row. */
    private String tenantIdOf(String siteId) {
        return inTenant(TurTenant.DEFAULT_TENANT_ID, () -> (String) entityManager
                .createNativeQuery("select tenantId from sn_site where id = :id")
                .setParameter("id", siteId)
                .getSingleResult());
    }

    /** Bind {@code tenant} then open a fresh transaction/session so Hibernate resolves it. */
    private <T> T inTenant(String tenant, Supplier<T> work) {
        return tenantContext.runAs(tenant, () -> tx.execute(status -> work.get()));
    }
}
