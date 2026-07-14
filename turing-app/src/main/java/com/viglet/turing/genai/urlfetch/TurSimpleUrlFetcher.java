/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.urlfetch;

import java.net.URL;

import org.springframework.stereotype.Component;

import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.utils.TurFileUtils;

/**
 * T739 / §XLVIII — the {@code SIMPLE} fetch backend: the legacy path, a plain
 * {@code HttpURLConnection} GET whose raw response is parsed by Tika (via
 * {@link TurFileUtils#urlContentToText(URL)}). Dependency-free and fast; a
 * JS-rendered SPA yields an empty shell and extracts little/no text — that is
 * exactly the case {@code AUTO} escalates to the headless backend.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurSimpleUrlFetcher implements TurUrlFetcher {

    @Override
    public TurFileAttributes fetch(URL url, TurUrlFetchConfig config) {
        TurFileAttributes attributes = TurFileUtils.urlContentToText(url);
        return attributes == null ? new TurFileAttributes() : attributes;
    }

    @Override
    public TurUrlFetchMode getBackend() {
        return TurUrlFetchMode.SIMPLE;
    }
}
