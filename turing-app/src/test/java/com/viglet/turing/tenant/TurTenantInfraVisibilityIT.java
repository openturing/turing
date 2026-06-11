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

import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.mcp.TurMcpServerConnectionType;
import com.viglet.turing.persistence.model.mcp.TurMcpServerType;
import com.viglet.turing.persistence.repository.mcp.TurMcpServerRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * T275 / §XIV.5.1 — proves the shared-infra visibility rule on
 * {@code mcp_server} (representative of all six BYO-infra tables): a tenant sees
 * its <em>own</em> instances plus the platform {@code null}-tenant global pool,
 * but never another tenant's private instances.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurTenantInfraVisibilityIT extends AbstractTuringSpringIT {

    @DynamicPropertySource
    static void enableTenancy(DynamicPropertyRegistry registry) {
        registry.add("turing.tenancy.enabled", () -> "true");
    }

    @Autowired
    private TurMcpServerRepository mcpServerRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
    }

    @Test
    void tenantSeesOwnPlusGlobalButNotOtherTenants() {
        String globalId = createServer(null, "global-" + UUID.randomUUID());
        String aId = createServer("tenantA", "a-" + UUID.randomUUID());
        String bId = createServer("tenantB", "b-" + UUID.randomUUID());

        List<String> visibleToA = inTx(() -> mcpServerRepository.findVisibleToTenant("tenantA"))
                .stream().map(TurMcpServer::getId).toList();

        assertThat(visibleToA).contains(globalId, aId).doesNotContain(bId);
    }

    private String createServer(String tenantId, String title) {
        return inTx(() -> {
            TurMcpServer server = new TurMcpServer();
            server.setId(UUID.randomUUID().toString());
            server.setTitle(title);
            server.setType(TurMcpServerType.SYNC);
            server.setConnectionType(TurMcpServerConnectionType.HTTP);
            server.setEnabled(1);
            server.setTenantId(tenantId);
            return mcpServerRepository.save(server).getId();
        });
    }

    private <T> T inTx(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }
}
