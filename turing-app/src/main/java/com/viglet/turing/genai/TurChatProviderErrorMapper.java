/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a streaming chat failure (an LLM provider exception surfaced as a
 * {@code Flux} error signal) into a short, <em>safe</em>, human-readable message
 * that can be emitted as an assistant turn.
 *
 * <p>Why this exists: a chat turn streams over Server-Sent Events, so the
 * response {@code Content-Type} is locked to {@code text/event-stream} before
 * the error happens. The global {@code @RestControllerAdvice} can no longer
 * write a JSON {@code ProblemDetail} body at that point, so the stream simply
 * breaks and the UI renders a <em>blank</em> assistant reply. Mapping the error
 * to an assistant message inside the {@code Flux} (via {@code onErrorResume})
 * lets the UI show that the turn failed instead of nothing.
 *
 * <p><b>The raw provider message is never echoed.</b> The chat endpoint can
 * serve unauthenticated widget visitors, and a provider error often carries
 * internal detail (model ids, organization/billing state, fix URLs). Following
 * the same posture as {@code TurGlobalExceptionHandler} ("never echo internal
 * exception details in the payload, only in the correlated server log"), this
 * returns a generic, category-aware message; the actionable detail stays in the
 * server log the caller writes alongside {@code onErrorResume}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurChatProviderErrorMapper {

    private TurChatProviderErrorMapper() {
    }

    /** A provider/auth/config problem the visitor can't fix — points at the admin. */
    static final String MSG_CONFIG =
            "⚠️ The assistant isn't available right now due to a configuration issue. "
                    + "Please contact your administrator.";
    /** Transient overload — retrying shortly may work. */
    static final String MSG_BUSY =
            "⚠️ The assistant is busy at the moment. Please wait a few seconds and try again.";
    /** Provider-side outage / timeout — retry later. */
    static final String MSG_UNAVAILABLE =
            "⚠️ The assistant is temporarily unavailable. Please try again shortly.";
    /** Anything else. */
    static final String MSG_GENERIC =
            "⚠️ Sorry, I couldn't complete that request. Please try again.";

    /** Portuguese counterpart of {@link #MSG_CONFIG}. */
    static final String PT_CONFIG =
            "⚠️ O assistente não está disponível no momento devido a um problema de configuração. "
                    + "Entre em contato com o administrador.";
    /** Portuguese counterpart of {@link #MSG_BUSY}. */
    static final String PT_BUSY =
            "⚠️ O assistente está ocupado no momento. Aguarde alguns segundos e tente novamente.";
    /** Portuguese counterpart of {@link #MSG_UNAVAILABLE}. */
    static final String PT_UNAVAILABLE =
            "⚠️ O assistente está temporariamente indisponível. Tente novamente em instantes.";
    /** Portuguese counterpart of {@link #MSG_GENERIC}. */
    static final String PT_GENERIC =
            "⚠️ Desculpe, não consegui concluir essa solicitação. Tente novamente.";

    /** Leading HTTP status many provider SDKs prefix onto the message, e.g. "403: …". */
    private static final Pattern LEADING_STATUS = Pattern.compile("^\\s*(\\d{3})\\b");

    /** Coarse, language-neutral failure category derived from the HTTP status. */
    private enum Category { CONFIG, BUSY, UNAVAILABLE, GENERIC }

    /**
     * Map a throwable to a safe, generic assistant message in English. Never
     * {@code null} or blank; no provider text is included.
     */
    public static String toUserMessage(Throwable error) {
        return toUserMessage(error, Locale.ENGLISH);
    }

    /**
     * Locale-aware variant: returns the Portuguese message when {@code locale}
     * is Portuguese (any region), otherwise English. The category is derived
     * from any HTTP status the provider exposed; no provider text is included.
     */
    public static String toUserMessage(Throwable error, Locale locale) {
        boolean pt = locale != null && "pt".equalsIgnoreCase(locale.getLanguage());
        return switch (categoryOf(error)) {
            case CONFIG -> pt ? PT_CONFIG : MSG_CONFIG;
            case BUSY -> pt ? PT_BUSY : MSG_BUSY;
            case UNAVAILABLE -> pt ? PT_UNAVAILABLE : MSG_UNAVAILABLE;
            case GENERIC -> pt ? PT_GENERIC : MSG_GENERIC;
        };
    }

    private static Category categoryOf(Throwable error) {
        int status = statusOf(error);
        if (status == 401 || status == 402 || status == 403) {
            return Category.CONFIG;
        }
        if (status == 429) {
            return Category.BUSY;
        }
        if (status >= 500 && status <= 599) {
            return Category.UNAVAILABLE;
        }
        return Category.GENERIC;
    }

    /**
     * Best-effort HTTP status extraction. Walks the cause chain and reads the
     * leading {@code "NNN:"} status the OpenAI/Anthropic SDKs prefix onto their
     * messages. Returns {@code -1} when none is found.
     */
    private static int statusOf(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                Matcher matcher = LEADING_STATUS.matcher(message);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return -1;
    }
}
