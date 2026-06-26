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
 * T136 / §X.3.d — the pluggable backend that actually executes the actions an
 * LLM computer-use loop emits (click / type / scroll / …) and returns a
 * screenshot of the resulting screen.
 *
 * <p>This is the seam that keeps the OpenAI Responses {@code computer_use}
 * built-in tool decoupled from <em>how</em> the screen is driven. The OpenAI
 * loop ({@code TurOpenAiComputerUseService}) speaks this interface and the SDK;
 * an implementation speaks this interface and a browser / VM / emulator. Turing
 * ships {@link TurNoOpComputerUseDriver} (advertised, never available) so the
 * capability is selectable in the matrix without bundling a heavyweight browser
 * dependency — a real driver is added later as a higher-priority bean. This
 * mirrors how the search-engine ({@code TurSearchEnginePlugin}) and storage
 * ({@code TurStorageService}) layers stay pluggable.
 *
 * <p>An implementation must be safe to call concurrently across distinct
 * {@link Session sessions}; a single session is single-threaded (the loop drives
 * it one action at a time).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurComputerUseDriver {

    /**
     * Whether this driver can start sessions in the current deployment. When
     * {@code false} the loop refuses the turn and replies with an explanatory
     * message instead of calling {@link #start}.
     */
    boolean isAvailable();

    /**
     * Open a new session against a fresh "computer".
     *
     * @param environment the kind of computer to drive
     * @param width       the virtual display width in pixels (must match the
     *                    {@code display_width} sent to the model)
     * @param height      the virtual display height in pixels
     * @param startUrl    for {@link TurComputerUseEnvironment#BROWSER}, the page
     *                    to open first; ignored (may be {@code null}) otherwise
     * @return a live session the caller must {@link Session#close() close}
     * @throws UnsupportedOperationException if {@link #isAvailable()} is {@code false}
     */
    Session start(TurComputerUseEnvironment environment, int width, int height, String startUrl);

    /**
     * A live computer-use session. Drives one screen for the lifetime of a single
     * chat turn; closed (and its resources released) when the loop ends.
     */
    interface Session extends AutoCloseable {

        /** Capture the current screen without performing any action. */
        TurComputerUseScreenshot screenshot();

        /** Perform one action, then capture and return the resulting screen. */
        TurComputerUseScreenshot execute(TurComputerUseAction action);

        @Override
        void close();
    }
}
