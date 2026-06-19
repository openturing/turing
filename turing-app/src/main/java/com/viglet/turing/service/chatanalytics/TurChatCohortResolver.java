/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.service.chatanalytics;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * T74 / §VII.8.e — resolves the three visitor <b>cohort</b> dimensions the
 * scorecard slices on (timezone / locale / device class) from the current
 * HTTP request:
 *
 * <ul>
 *   <li><b>deviceType</b> — classified from the {@code User-Agent} header
 *       into {@code mobile} / {@code tablet} / {@code desktop} / {@code bot}
 *       / {@code unknown}. Always available (every browser sends a UA).</li>
 *   <li><b>locale</b> — the primary language tag from {@code Accept-Language}
 *       (e.g. {@code "pt-BR"} from {@code "pt-BR,pt;q=0.9,en;q=0.8"}). Also a
 *       standard browser header, so populated without client cooperation.</li>
 *   <li><b>timezone</b> — the IANA id the client sent in the custom
 *       {@code X-Timezone} header (the SDK fills it from
 *       {@code Intl.DateTimeFormat().resolvedOptions().timeZone}). {@code null}
 *       when absent — browsers don't expose the timezone in any standard
 *       header.</li>
 * </ul>
 *
 * <p>{@link #resolveFromCurrentRequest()} reads the request bound to the
 * current thread via {@link RequestContextHolder}. The chat executor calls it
 * from the synchronous prologue of {@code execute(...)} — the same HTTP thread
 * that {@code resolveUsername()} reads {@code SecurityContextHolder} on, before
 * the reactive SSE {@code Flux} starts on a worker thread. Off-request callers
 * (e.g. a Custom Tool's {@code agent.invoke(...)} child session) have no bound
 * request and get an all-{@code null} {@link Cohort} — correct, since a child
 * session has no visitor cohort of its own.
 *
 * <p>The classification helpers ({@link #classifyDeviceType(String)},
 * {@link #parseLocale(String)}) are pure static functions, unit-tested without
 * a Spring context.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Component
public class TurChatCohortResolver {

    /** Custom request header the SDK sets with the browser's IANA timezone id. */
    public static final String TIMEZONE_HEADER = "X-Timezone";

    public static final String DEVICE_MOBILE = "mobile";
    public static final String DEVICE_TABLET = "tablet";
    public static final String DEVICE_DESKTOP = "desktop";
    public static final String DEVICE_BOT = "bot";
    public static final String DEVICE_UNKNOWN = "unknown";

    /**
     * Immutable bag of the three cohort dimensions. Any field may be
     * {@code null} (absent / unresolved) — the analytics store skips blank
     * values on write.
     */
    public record Cohort(String locale, String timezone, String deviceType) {
        /** Empty cohort — used when there's no request bound to the thread. */
        public static Cohort empty() {
            return new Cohort(null, null, null);
        }
    }

    /**
     * Resolves the cohort from the HTTP request bound to the current thread.
     * Returns {@link Cohort#empty()} when no request is bound (off-request
     * call) or anything goes wrong — best-effort, never throws.
     */
    public Cohort resolveFromCurrentRequest() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
                return Cohort.empty();
            }
            HttpServletRequest request = servletAttrs.getRequest();
            String locale = parseLocale(request.getHeader("Accept-Language"));
            String timezone = normalizeTimezone(request.getHeader(TIMEZONE_HEADER));
            String deviceType = classifyDeviceType(request.getHeader("User-Agent"));
            return new Cohort(locale, timezone, deviceType);
        } catch (RuntimeException e) {
            return Cohort.empty();
        }
    }

    /**
     * Classifies a {@code User-Agent} string into a coarse device class. Order
     * matters: bots first (many crawlers spoof "Mobile"); tablets before
     * phones ("iPad" UAs also carry "Mobile"); Android phones carry "Mobile"
     * while Android tablets don't.
     *
     * @return one of {@code bot}/{@code tablet}/{@code mobile}/{@code desktop},
     *         or {@code unknown} for a null/blank UA.
     */
    public static String classifyDeviceType(String userAgent) {
        if (StringUtils.isBlank(userAgent)) return DEVICE_UNKNOWN;
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (isBot(ua)) return DEVICE_BOT;
        if (isTablet(ua)) return DEVICE_TABLET;
        if (isMobile(ua)) return DEVICE_MOBILE;
        return DEVICE_DESKTOP;
    }

    private static boolean isBot(String ua) {
        return ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")
                || ua.contains("slurp") || ua.contains("bingpreview")
                || ua.contains("facebookexternalhit") || ua.contains("headlesschrome")
                || ua.contains("python-requests") || ua.contains("curl/")
                || ua.contains("httpclient") || ua.contains("okhttp");
    }

    private static boolean isTablet(String ua) {
        // iPad, or an Android device that is explicitly NOT DEVICE_MOBILE.
        if (ua.contains("ipad")) return true;
        if (ua.contains(DEVICE_TABLET) || ua.contains("kindle") || ua.contains("playbook")) return true;
        return ua.contains("android") && !ua.contains(DEVICE_MOBILE);
    }

    private static boolean isMobile(String ua) {
        return ua.contains(DEVICE_MOBILE) || ua.contains("iphone") || ua.contains("ipod")
                || ua.contains("android") || ua.contains("windows phone")
                || ua.contains("blackberry") || ua.contains("opera mini");
    }

    /**
     * Extracts the primary language tag from an {@code Accept-Language} header,
     * stripping the {@code q=} weights. {@code "pt-BR,pt;q=0.9,en;q=0.8"} →
     * {@code "pt-BR"}. Returns {@code null} for null/blank/wildcard-only input.
     */
    public static String parseLocale(String acceptLanguage) {
        if (StringUtils.isBlank(acceptLanguage)) return null;
        // First comma-separated entry, before any ";q=" weight.
        String first = acceptLanguage.split(",", 2)[0];
        String tag = first.split(";", 2)[0].trim();
        if (tag.isEmpty() || "*".equals(tag)) return null;
        return tag;
    }

    private static String normalizeTimezone(String raw) {
        if (StringUtils.isBlank(raw)) return null;
        String tz = raw.trim();
        // Defensive cap — a legitimate IANA id is well under 64 chars; anything
        // longer is junk / an injection attempt and gets dropped.
        return tz.length() > 64 ? null : tz;
    }
}
