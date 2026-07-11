/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.spring.security.abuse;

import java.io.IOException;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.viglet.turing.properties.TurAbuseControlProperty;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * T641 / §XXXVII.3 — per-IP / per-session request throttle for the anonymous
 * public chat + search surface. The public SN endpoints are {@code permitAll}
 * by design and the shipped nginx config provided no {@code limit_req}, so a
 * loop of {@code POST /api/sn/{site}/chat/conversation} (each turn = embedding
 * + retrieval + completion) could drive unbounded LLM spend. This in-process
 * fixed-window limiter is the app-layer backstop that does not depend on a
 * correctly-configured reverse proxy.
 *
 * <p>Only the anonymous cost/abuse-sensitive paths are throttled; everything
 * else passes straight through. Over the limit → HTTP 429 with a
 * {@code Retry-After} header. Enabled by default, tunable via
 * {@code turing.abuse.chat.*}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurAnonymousAbuseRateLimitFilter extends OncePerRequestFilter {

    /**
     * The anonymous, <b>LLM-cost-bearing</b> SN endpoints: RAG chat and the
     * persona content-fit call. Cheap high-frequency reads (autocomplete
     * {@code /ac}, {@code /search}, {@code /query}) are deliberately NOT limited
     * here — throttling per-keystroke autocomplete would break normal search UX;
     * they are shed at the reverse-proxy layer with a looser zone instead. This
     * filter is the app-layer backstop for the unbounded-spend chat surface
     * (§XXXVII.3). Admin/console paths never match a site name so are never hit.
     */
    private static final Pattern RATE_LIMITED_PATH = Pattern.compile(
            "^/api/sn/[^/]+/chat(?:/.*)?$"
                    + "|^/api/sn/[^/]+/persona/[^/]+/content-fit$");

    private final TurAbuseControlProperty properties;
    private final TurFixedWindowRateLimiter ipLimiter;
    private final TurFixedWindowRateLimiter sessionLimiter;
    private final int retryAfterSeconds;

    public TurAnonymousAbuseRateLimitFilter(TurAbuseControlProperty properties) {
        this.properties = properties;
        TurAbuseControlProperty.Chat chat = properties.getChat();
        long windowMillis = Math.max(1, chat.getWindowSeconds()) * 1000L;
        this.retryAfterSeconds = Math.max(1, chat.getWindowSeconds());
        this.ipLimiter = new TurFixedWindowRateLimiter(chat.getRequestsPerMinutePerIp(), windowMillis);
        this.sessionLimiter = new TurFixedWindowRateLimiter(chat.getRequestsPerMinutePerSession(), windowMillis);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response,
            @NotNull FilterChain filterChain) throws ServletException, IOException {
        if (!properties.getChat().isRateLimitEnabled() || !isRateLimited(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        String session = sessionKey(request);
        boolean ipOk = ipLimiter.tryAcquire(ip);
        boolean sessionOk = sessionLimiter.tryAcquire(session);

        if (ipOk && sessionOk) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("[AbuseControl] Rate limit exceeded for {} (ip={}, session={})",
                request.getRequestURI(), ip, session);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"Too Many Requests\"}");
    }

    private boolean isRateLimited(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        return RATE_LIMITED_PATH.matcher(uri).find();
    }

    /**
     * Best-effort client IP. Trusts the left-most {@code X-Forwarded-For} hop /
     * {@code X-Real-IP} when present (the reverse proxy sets them), else the
     * socket address. Header spoofing only lets an attacker throttle themselves
     * more aggressively across the two dimensions; the session limiter is the
     * spoof-resistant companion.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /** Session dimension: the conversationId query param when present, else the HTTP session id. */
    private static String sessionKey(HttpServletRequest request) {
        String conversationId = request.getParameter("conversationId");
        if (conversationId != null && !conversationId.isBlank()) {
            return "conv:" + conversationId.trim();
        }
        var session = request.getSession(false);
        return session != null ? "sess:" + session.getId() : "sess:none";
    }
}
