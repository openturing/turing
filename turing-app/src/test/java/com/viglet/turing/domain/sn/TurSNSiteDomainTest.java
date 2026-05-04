/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.domain.sn;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Behaviour-only tests for the {@link TurSNSiteDomain} aggregate record. */
class TurSNSiteDomainTest {

    @Test
    void hasSearchTemplateIsTrueForNonBlankValues() {
        TurSNSiteDomain site = withTemplate("/sn/foo/index.html");
        assertThat(site.hasSearchTemplate()).isTrue();
    }

    @Test
    void hasSearchTemplateIsFalseForNull() {
        TurSNSiteDomain site = withTemplate(null);
        assertThat(site.hasSearchTemplate()).isFalse();
    }

    @Test
    void hasSearchTemplateIsFalseForBlankValues() {
        assertThat(withTemplate("").hasSearchTemplate()).isFalse();
        assertThat(withTemplate("   ").hasSearchTemplate()).isFalse();
    }

    private static TurSNSiteDomain withTemplate(String template) {
        return new TurSNSiteDomain("id", "name", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, template, null, null, null);
    }
}
