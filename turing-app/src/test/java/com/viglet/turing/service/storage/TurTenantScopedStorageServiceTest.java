/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.tenant.TurTenantContext;

/**
 * Unit tests for {@link TurTenantScopedStorageService} (T269).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurTenantScopedStorageServiceTest {

    @Mock
    private TurStorageService delegate;

    private TurTenantContext context;

    private TurTenantScopedStorageService scoped(boolean tenancyEnabled) {
        TurConfigProperties props = new TurConfigProperties();
        props.getTenancy().setEnabled(tenancyEnabled);
        context = new TurTenantContext(props);
        return new TurTenantScopedStorageService(delegate, context);
    }

    @AfterEach
    void clear() {
        if (context != null) {
            context.clear();
        }
    }

    @Test
    void prependsTenantPrefixOnDownloadWhenBound() {
        TurTenantScopedStorageService service = scoped(true);
        context.runAs("acme", () -> {
            service.downloadObject("docs/a.pdf");
            return null;
        });
        verify(delegate).downloadObject(eq("tenants/acme/docs/a.pdf"));
    }

    @Test
    void passesThroughUnchangedWhenTenancyOff() {
        TurTenantScopedStorageService service = scoped(false);
        service.downloadObject("docs/a.pdf");
        verify(delegate).downloadObject(eq("docs/a.pdf"));
    }

    @Test
    void passesThroughForDefaultTenant() {
        TurTenantScopedStorageService service = scoped(true); // nothing bound -> DEFAULT
        service.deleteObject("x.bin");
        verify(delegate).deleteObject(eq("x.bin"));
    }

    @Test
    void stripsTenantPrefixFromListings() {
        TurTenantScopedStorageService service = scoped(true);
        when(delegate.listObjects(eq("tenants/acme/")))
                .thenReturn(List.of(new TurAssetItem("tenants/acme/docs/a.pdf", 1, "application/pdf",
                        "now", false)));

        List<TurAssetItem> result = context.runAs("acme", () -> service.listObjects(""));

        assertThat(result).singleElement()
                .satisfies(i -> assertThat(i.name()).isEqualTo("docs/a.pdf"));
    }

    @Test
    void rejectsPathTraversalOutOfTenantRoot() {
        TurTenantScopedStorageService service = scoped(true);
        assertThatThrownBy(() -> context.runAs("acme",
                () -> service.downloadObject("../other/secret.pdf")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traversal");
    }
}
