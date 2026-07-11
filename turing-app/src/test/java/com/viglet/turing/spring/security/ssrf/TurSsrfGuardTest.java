/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.spring.security.ssrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * T643 / §XXXVII.5 — unit tests for the central SSRF egress guard. URLs use
 * literal IPs so {@code InetAddress.getAllByName} returns without a DNS lookup,
 * keeping the test hermetic.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSsrfGuardTest {

    private final TurSsrfGuard guard = new TurSsrfGuard();

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",   // loopback
            "10.0.0.1",    // RFC1918 /8
            "172.16.5.4",  // RFC1918 /12
            "192.168.1.1", // RFC1918 /16
            "100.64.0.1",  // CGNAT
            "169.254.169.254", // link-local (cloud metadata)
            "0.0.0.0"      // any-local
    })
    void blocksPrivateAndSpecialIpv4(String ip) throws UnknownHostException {
        assertThat(TurSsrfGuard.isSafeAddress(InetAddress.getByName(ip))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "8.8.8.8", "1.1.1.1", "93.184.216.34" })
    void allowsPublicIpv4(String ip) throws UnknownHostException {
        assertThat(TurSsrfGuard.isSafeAddress(InetAddress.getByName(ip))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "::1", "fc00::1", "fd12:3456:789a::1", "fe80::1" })
    void blocksLoopbackLinkLocalAndUlaIpv6(String ip) throws UnknownHostException {
        // fc00::/7 (ULA) is the gap the old guard missed.
        assertThat(TurSsrfGuard.isSafeAddress(InetAddress.getByName(ip))).isFalse();
    }

    @Test
    void allowsPublicIpv6() throws UnknownHostException {
        assertThat(TurSsrfGuard.isSafeAddress(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
    }

    @Test
    void nullAddressIsUnsafe() {
        assertThat(TurSsrfGuard.isSafeAddress(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/",
            "https://10.0.0.1/x",
            "http://[fc00::1]/",
            "http://169.254.169.254/latest/meta-data/",
            "ftp://8.8.8.8/",       // disallowed protocol
            "file:///etc/passwd",   // disallowed protocol
            "not a url",
            "http://",
            ""
    })
    void isAllowedUrlRejectsUnsafeOrMalformed(String url) {
        assertThat(guard.isAllowedUrl(url)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "http://8.8.8.8/", "https://1.1.1.1/path?q=1" })
    void isAllowedUrlAcceptsPublic(String url) {
        assertThat(guard.isAllowedUrl(url)).isTrue();
    }

    @Test
    void assertAllowedUrlThrowsOnBlocked() {
        assertThatThrownBy(() -> guard.assertAllowedUrl("http://127.0.0.1/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SSRF");
    }

    @Test
    void assertResolvedSafeThrowsOnPrivateHost() {
        assertThatThrownBy(() -> guard.assertResolvedSafe("10.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
