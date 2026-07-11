/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.spring.security.ssrf;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * T643 / §XXXVII.5 — the single hardened egress validator. Turing already had a
 * good guard buried in {@code TurFileUtils} (used only by the persona URL
 * import); every other outbound feature bypassed it. This is the reusable
 * component the outbound sinks route through — the connector test-connection
 * oracle, the integration federation proxy, and admin-configured base URLs.
 *
 * <p>It blocks requests to loopback / link-local / any-local / multicast /
 * private IPv4 (RFC1918 + CGNAT) <b>and</b> IPv6 Unique-Local Addresses
 * ({@code fc00::/7}) — the gap in the original guard, whose
 * {@link InetAddress#isSiteLocalAddress()} only matches the deprecated
 * {@code fec0::/10}. Because a hostname can resolve to several addresses, ALL
 * resolved addresses must be safe (a mixed public/private answer is blocked).
 *
 * <p><b>DNS-rebinding note:</b> {@link #isAllowedUrl} resolves the host to
 * validate it, but the subsequent HTTP client resolves it again — a classic
 * TOCTOU window. For call sites that can, prefer {@link #assertResolvedSafe}
 * on every redirect hop / immediately before connecting. Pinning the exact
 * resolved IP into the connection is the strongest defense and is applied by
 * {@code TurFileUtils} for the download path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurSsrfGuard {

    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("http", "https");

    /**
     * Canonical address-safety check. {@code false} = must not be reached from
     * an untrusted / attacker-influenceable URL.
     */
    public static boolean isSafeAddress(InetAddress address) {
        if (address == null) {
            return false;
        }
        if (address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || address.isLinkLocalAddress()
                || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            return isSafeInet4(address.getAddress());
        }
        if (address instanceof Inet6Address inet6) {
            return isSafeInet6(inet6);
        }
        return true;
    }

    private static boolean isSafeInet4(byte[] addr) {
        int first = addr[0] & 0xFF;
        int second = addr[1] & 0xFF;
        // 10.0.0.0/8
        if (first == 10) {
            return false;
        }
        // 172.16.0.0/12
        if (first == 172 && second >= 16 && second <= 31) {
            return false;
        }
        // 192.168.0.0/16
        if (first == 192 && second == 168) {
            return false;
        }
        // 100.64.0.0/10 (CGNAT)
        return !(first == 100 && second >= 64 && second <= 127);
    }

    private static boolean isSafeInet6(Inet6Address address) {
        byte[] addr = address.getAddress();
        int first = addr[0] & 0xFF;
        // fc00::/7 — IPv6 Unique-Local Addresses (the gap in the old guard).
        if ((first & 0xFE) == 0xFC) {
            return false;
        }
        // An IPv4-mapped IPv6 address (::ffff:a.b.c.d) must be judged on its IPv4.
        if (address.isIPv4CompatibleAddress()) {
            byte[] v4 = new byte[] { addr[12], addr[13], addr[14], addr[15] };
            return isSafeInet4(v4);
        }
        return true;
    }

    /**
     * True when {@code urlString} uses an allowed protocol and every resolved
     * address is safe. Never throws — a malformed URL or unknown host → false.
     */
    public boolean isAllowedUrl(String urlString) {
        if (!StringUtils.hasText(urlString)) {
            return false;
        }
        final URI uri;
        try {
            uri = new URI(urlString.trim());
        } catch (URISyntaxException e) {
            return false;
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)
                || !ALLOWED_PROTOCOLS.contains(scheme.toLowerCase())) {
            return false;
        }
        try {
            InetAddress[] resolved = InetAddress.getAllByName(host);
            if (resolved.length == 0) {
                return false;
            }
            for (InetAddress address : resolved) {
                if (!isSafeAddress(address)) {
                    log.warn("[SSRF] Blocked URL '{}' — host {} resolves to unsafe address {}",
                            urlString, host, address.getHostAddress());
                    return false;
                }
            }
            return true;
        } catch (UnknownHostException e) {
            log.warn("[SSRF] Blocked URL '{}' — unknown host {}", urlString, host);
            return false;
        }
    }

    /**
     * Throws {@link IllegalArgumentException} (mapped to HTTP 400 by the global
     * handler) when {@code urlString} is not an allowed egress target.
     */
    public void assertAllowedUrl(String urlString) {
        if (!isAllowedUrl(urlString)) {
            throw new IllegalArgumentException(
                    "The URL is not an allowed outbound target (blocked by the SSRF egress guard).");
        }
    }

    /**
     * Re-validate a host that a redirect / hop resolved to, for call sites that
     * follow redirects with their own HTTP client. Throws on an unsafe target.
     */
    public void assertResolvedSafe(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (!isSafeAddress(address)) {
                    throw new IllegalArgumentException(
                            "Redirect target resolves to a blocked address: " + host);
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Redirect target host is unknown: " + host, e);
        }
    }
}
