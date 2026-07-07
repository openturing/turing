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
package com.viglet.turing.genai.nativeapi.openai.computeruse;

import java.util.Locale;

/**
 * T136 / §X.3.d — the kind of "computer" a {@link TurComputerUseDriver} drives.
 *
 * <p>Vendor-neutral on purpose: the seam never exposes the OpenAI SDK's
 * {@code ComputerUsePreviewTool.Environment} so a driver implementation (a
 * headless browser, a remote VM, …) carries no dependency on the LLM SDK. The
 * OpenAI loop maps this enum onto the SDK type at the boundary.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurComputerUseEnvironment {

    BROWSER,
    MAC,
    WINDOWS,
    UBUNTU,
    LINUX;

    /** Lenient parse used to read the per-capability {@code environment} config value. */
    public static TurComputerUseEnvironment fromString(String value, TurComputerUseEnvironment fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
