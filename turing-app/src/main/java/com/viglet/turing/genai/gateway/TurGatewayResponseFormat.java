/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.gateway;

import java.util.Locale;

import com.viglet.turing.genai.TurUnsupportedParameterException;
import com.viglet.turing.genai.gateway.TurOpenAiWire.ResponseFormat;

/**
 * T1090 — the gateway's own handling of an OpenAI {@code response_format}: the
 * cache slot it gets and the refusal on Turing-routed models, which answer
 * prose. Applying it to a provider call is {@code TurPromptOverrides}, through
 * {@link TurGatewayPromptOptions}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
final class TurGatewayResponseFormat {

    static final String PARAM = "response_format";
    private static final String TEXT = "text";

    private TurGatewayResponseFormat() {
    }

    /** True when the request asks for anything other than plain text. */
    static boolean requested(ResponseFormat format) {
        return format != null && !TEXT.equals(normalisedType(format));
    }

    /** Suffix that keeps a JSON-mode answer out of the plain-text cache slot. */
    static String cacheModelKey(String model, ResponseFormat format) {
        return requested(format) ? model + "|" + PARAM + "=" + normalisedType(format) : model;
    }

    /** Refuses a JSON-mode request on a path that answers without a provider call. */
    static void rejectIfRequested(ResponseFormat format, String model) {
        if (requested(format)) {
            throw new TurUnsupportedParameterException(PARAM, PARAM + " is not supported for model " + model);
        }
    }

    private static String normalisedType(ResponseFormat format) {
        return format.type() == null ? TEXT : format.type().trim().toLowerCase(Locale.ROOT);
    }
}
