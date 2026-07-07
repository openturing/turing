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

/**
 * T136 / §X.3.d — the state a {@link TurComputerUseDriver} hands back after an
 * action: a PNG screenshot the model uses to decide the next step, plus the
 * current location for logging.
 *
 * @param pngBase64  Base64-encoded PNG bytes of the current screen (no
 *                   {@code data:} prefix — the OpenAI loop wraps it into a data
 *                   URI before sending it back as the {@code computer_call_output})
 * @param currentUrl the current page URL for a browser environment, or any
 *                   human-readable location label; may be {@code null}
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurComputerUseScreenshot(String pngBase64, String currentUrl) {

    public TurComputerUseScreenshot(String pngBase64) {
        this(pngBase64, null);
    }
}
