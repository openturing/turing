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
 * T136 / §X.3.d — the default {@link TurComputerUseDriver}: always unavailable.
 *
 * <p>Ships so the {@code openai-computer-use} capability is selectable in the
 * matrix and the screenshot/action loop is fully wired and testable, <em>without</em>
 * Turing bundling a browser automation dependency. {@link #isAvailable()} returns
 * {@code false}, so the loop replies with an explanatory message rather than
 * attempting a turn. A real driver (e.g. Playwright-backed) is added in a future
 * change as a higher-priority bean; this no-op then steps aside via
 * {@code @ConditionalOnMissingBean} (see {@code TurComputerUseDriverConfig}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurNoOpComputerUseDriver implements TurComputerUseDriver {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Session start(TurComputerUseEnvironment environment, int width, int height, String startUrl) {
        throw new UnsupportedOperationException(
                "No computer-use driver is configured. The openai-computer-use capability is "
                        + "advertised but needs a TurComputerUseDriver bean (e.g. a browser backend) "
                        + "to execute actions.");
    }
}
