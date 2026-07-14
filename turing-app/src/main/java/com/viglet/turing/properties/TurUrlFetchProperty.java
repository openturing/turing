/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.properties;

import com.viglet.turing.genai.urlfetch.TurUrlFetchMode;

import lombok.Getter;
import lombok.Setter;

/**
 * T739 / §XLVIII — boot-time URL content-fetch configuration bound to
 * {@code turing.url-fetch.*}. Lets headless / Viglet Cloud deploys pin the fetch
 * mode and the {@code browserless} sidecar per container without touching the
 * Global Settings UI, exactly the way {@code turing.transcription.*} pins the
 * transcription backend.
 *
 * <p>Any value left {@code null}/blank falls back to the DB Global Settings value
 * (then to the hardcoded default), so an unset namespace means "use the
 * UI-configured mode" — see {@code TurUrlFetchConfigResolver}. Non-blank values
 * here <b>win</b> over the DB row (the container pins the mode).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
public class TurUrlFetchProperty {

    /** Active fetch mode. {@code null} = fall back to the UI-configured mode. */
    private TurUrlFetchMode mode;

    /**
     * Base URL of the {@code browserless/chromium} sidecar (e.g.
     * {@code http://browserless:3000}). Required for {@code HEADLESS} and for the
     * {@code AUTO} escalation; blank = fall back to the UI value.
     */
    private String browserlessUrl;

    /** Optional {@code browserless} auth token (its {@code TOKEN}); blank = none. */
    private String browserlessToken;

    /** Per-request timeout in seconds for a headless render. Clamped ≥ 1; default 30. */
    private int timeoutSeconds = 30;

    /**
     * In {@code AUTO} mode, the minimum number of extracted characters the cheap
     * {@code SIMPLE} path must yield to be accepted. Below this the fetch escalates
     * to {@code HEADLESS} (the SPA-shell case, where Tika extracts little or
     * nothing). Clamped ≥ 0; default 200.
     */
    private int autoMinChars = 200;
}
