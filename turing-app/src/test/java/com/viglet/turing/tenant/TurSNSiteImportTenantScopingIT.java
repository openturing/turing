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
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Reproduces the marketplace/import isolation bug: the import path assigns a
 * pre-existing id before saving, so Spring Data routes through
 * {@code EntityManager.merge()} rather than {@code persist()}. This test proves
 * the {@code @TenantId} discriminator is stamped (and reads filtered) on that
 * merge path the same way the {@code persist()} path is covered by
 * {@link TurSNSiteTenantIsolationIT}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurSNSiteImportTenantScopingIT extends AbstractTuringSpringIT {

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
    @Autowired
    private TurSNSiteRepository turSNSiteRepository;
    @Autowired
    private com.viglet.turing.exchange.sn.TurSNSiteImport turSNSiteImport;

    private TransactionTemplate tx;
    private String instanceId;
    private String vendorId;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        instanceId = inTenant(TurTenant.DEFAULT_TENANT_ID, () -> {
            TurSEVendor vendor = new TurSEVendor();
            vendor.setId(UUID.randomUUID().toString().substring(0, 8));
            vendor.setTitle("Test Vendor");
            entityManager.persist(vendor);
            vendorId = vendor.getId();

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
    void importWithAssignedIdIsStampedAndFilteredByTenant() {
        // Mirror the import path: id is assigned up front, so save() -> merge().
        String assignedId = UUID.randomUUID().toString();
        inTenant(TENANT_A, () -> {
            TurSNSite site = new TurSNSite();
            site.setId(assignedId);
            site.setName("imported-" + assignedId);
            site.setTurSEInstance(entityManager.find(TurSEInstance.class, instanceId));
            return turSNSiteRepository.saveAndFlush(site);
        });

        assertThat(tenantIdOf(assignedId)).isEqualTo(TENANT_A);

        List<String> seenByA = listSiteIds(TENANT_A);
        assertThat(seenByA).contains(assignedId);

        // Seed a tenant-B-owned site so the listing is genuinely non-empty: this
        // proves B sees its own data while A's site is filtered out, rather than
        // the assertion passing vacuously on an empty list.
        String bSiteId = seedSite(TENANT_B);

        List<String> seenByB = listSiteIds(TENANT_B);
        assertThat(seenByB).contains(bSiteId).doesNotContain(assignedId);
    }

    /**
     * The admin listing endpoint calls the {@code @Cacheable}
     * {@code findAllForListing()}, not a raw JPQL query. This exercises the
     * Spring cache layer: if its key is not tenant-partitioned, tenant B reads
     * the list tenant A populated.
     */
    @Test
    void cachedListingIsTenantPartitioned() {
        String assignedId = UUID.randomUUID().toString();
        inTenant(TENANT_A, () -> {
            TurSNSite site = new TurSNSite();
            site.setId(assignedId);
            site.setName("cached-" + assignedId);
            site.setTurSEInstance(entityManager.find(TurSEInstance.class, instanceId));
            return turSNSiteRepository.saveAndFlush(site);
        });

        // Seed a tenant-B-owned site so B's listing is genuinely non-empty.
        String bSiteId = seedSite(TENANT_B);

        // Tenant A warms the cache first, then tenant B reads through it.
        List<String> seenByA = inTenant(TENANT_A,
                () -> turSNSiteRepository.findAllForListing().stream().map(TurSNSite::getId).toList());
        List<String> seenByB = inTenant(TENANT_B,
                () -> turSNSiteRepository.findAllForListing().stream().map(TurSNSite::getId).toList());

        assertThat(seenByA).contains(assignedId);
        assertThat(seenByB).contains(bSiteId).doesNotContain(assignedId);
    }

    /**
     * The marketplace/import flow creates the referenced Search Engine instance
     * (a "bring-your-own-infra" entity with a plain {@code tenantId} column) on
     * the fly. Before the fix it was saved without stamping, defaulting to
     * {@code null} = GLOBAL, so it surfaced in <em>every</em> tenant's
     * infrastructure list. It must be claimed for the importing tenant instead.
     */
    @Test
    void importedSearchEngineInstanceIsClaimedByImportingTenant() {
        String newSeId = UUID.randomUUID().toString();
        String siteId = UUID.randomUUID().toString();

        com.viglet.turing.exchange.TurExchange exchange = new com.viglet.turing.exchange.TurExchange();

        TurSEInstance se = new TurSEInstance();
        se.setId(newSeId);
        se.setTitle("imported-se-" + newSeId);
        se.setEndpointUrl("http://localhost:0");
        se.setEnabled(1);
        TurSEVendor vendorRef = new TurSEVendor();
        vendorRef.setId(vendorId);
        se.setTurSEVendor(vendorRef);
        exchange.setSe(List.of(se));

        com.viglet.turing.exchange.sn.TurSNSiteExchange site =
                new com.viglet.turing.exchange.sn.TurSNSiteExchange();
        site.setId(siteId);
        site.setName("import-site-" + siteId);
        site.setTurSEInstance(newSeId);
        exchange.setSnSites(List.of(site));

        // importSNSite is @Transactional — it opens its own session, which
        // resolves the tenant bound by runAs (no outer tx needed here).
        tenantContext.runAs(TENANT_A, () -> turSNSiteImport.importSNSite(exchange));

        // tenantId is a plain column (not @TenantId), so findById is unscoped —
        // read it from any context and assert the importing tenant owns it.
        assertThat(seInstanceExists(newSeId)).isTrue();
        assertThat(seTenantIdOf(newSeId)).isEqualTo(TENANT_A);
    }

    /**
     * A startup/seed import ({@code TurExportImportOnStartup}) runs with no
     * tenant bound. The referenced infra must stay GLOBAL ({@code tenantId =
     * null}) so the platform-provided pool remains visible to every tenant —
     * stamping the DEFAULT fallback here would hide it from other tenants.
     */
    @Test
    void seedImportWithNoBoundTenantKeepsInfraGlobal() {
        String newSeId = UUID.randomUUID().toString();
        String siteId = UUID.randomUUID().toString();

        com.viglet.turing.exchange.TurExchange exchange = new com.viglet.turing.exchange.TurExchange();
        TurSEInstance se = new TurSEInstance();
        se.setId(newSeId);
        se.setTitle("seed-se-" + newSeId);
        se.setEndpointUrl("http://localhost:0");
        se.setEnabled(1);
        TurSEVendor vendorRef = new TurSEVendor();
        vendorRef.setId(vendorId);
        se.setTurSEVendor(vendorRef);
        exchange.setSe(List.of(se));

        com.viglet.turing.exchange.sn.TurSNSiteExchange site =
                new com.viglet.turing.exchange.sn.TurSNSiteExchange();
        site.setId(siteId);
        site.setName("seed-site-" + siteId);
        site.setTurSEInstance(newSeId);
        exchange.setSnSites(List.of(site));

        // No runAs and no outer tx: getCurrentTenant() is null and importSNSite
        // manages its own transaction, mirroring the startup runner exactly.
        turSNSiteImport.importSNSite(exchange);

        assertThat(seInstanceExists(newSeId)).isTrue();
        assertThat(seTenantIdOf(newSeId)).isNull();
    }

    /** Whether the SE instance row exists, read via native SQL (bypasses the cache). */
    private boolean seInstanceExists(String seId) {
        return inTenant(TurTenant.DEFAULT_TENANT_ID, () -> ((Number) entityManager
                .createNativeQuery("select count(*) from se_instance where id = :id")
                .setParameter("id", seId)
                .getSingleResult()).intValue() == 1);
    }

    /** Raw tenantId column of the SE instance, read via native SQL (bypasses the cache). */
    private String seTenantIdOf(String seId) {
        return inTenant(TurTenant.DEFAULT_TENANT_ID, () -> (String) entityManager
                .createNativeQuery("select tenantId from se_instance where id = :id")
                .setParameter("id", seId)
                .getSingleResult());
    }

    /** Creates a site owned by {@code tenant} and returns its assigned id. */
    private String seedSite(String tenant) {
        String siteId = UUID.randomUUID().toString();
        inTenant(tenant, () -> {
            TurSNSite site = new TurSNSite();
            site.setId(siteId);
            site.setName("seeded-" + siteId);
            site.setTurSEInstance(entityManager.find(TurSEInstance.class, instanceId));
            return turSNSiteRepository.saveAndFlush(site);
        });
        return siteId;
    }

    private List<String> listSiteIds(String tenant) {
        return inTenant(tenant, () -> entityManager
                .createQuery("select s.id from TurSNSite s", String.class)
                .getResultList());
    }

    private String tenantIdOf(String siteId) {
        return inTenant(TurTenant.DEFAULT_TENANT_ID, () -> (String) entityManager
                .createNativeQuery("select tenantId from sn_site where id = :id")
                .setParameter("id", siteId)
                .getSingleResult());
    }

    private <T> T inTenant(String tenant, Supplier<T> work) {
        return tenantContext.runAs(tenant, () -> tx.execute(status -> work.get()));
    }
}
