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

import java.util.List;

/**
 * T136 / §X.3.d — one vendor-neutral action the model asked the
 * {@link TurComputerUseDriver} to perform on the "computer".
 *
 * <p>This is the seam's translation of OpenAI's
 * {@code ResponseComputerToolCall.Action} union, so a driver implementation
 * never depends on the OpenAI SDK. Only the fields relevant to {@link #kind()}
 * are populated; the rest are {@code null} / empty.
 *
 * @param kind     which action to perform
 * @param x        target X coordinate (CLICK / DOUBLE_CLICK / MOVE / SCROLL)
 * @param y        target Y coordinate (CLICK / DOUBLE_CLICK / MOVE / SCROLL)
 * @param button   mouse button for CLICK ({@code "left"} / {@code "right"} / …)
 * @param keys     key names for KEYPRESS (e.g. {@code ["CTRL", "A"]})
 * @param text     literal text for TYPE
 * @param scrollX  horizontal scroll delta for SCROLL
 * @param scrollY  vertical scroll delta for SCROLL
 * @param path     ordered way-points for DRAG
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurComputerUseAction(
        Kind kind,
        Integer x,
        Integer y,
        String button,
        List<String> keys,
        String text,
        Integer scrollX,
        Integer scrollY,
        List<Point> path) {

    /** The set of actions the OpenAI computer-use tool can emit. */
    public enum Kind {
        CLICK,
        DOUBLE_CLICK,
        DRAG,
        KEYPRESS,
        MOVE,
        SCREENSHOT,
        SCROLL,
        TYPE,
        WAIT
    }

    /** A single coordinate on the virtual screen. */
    public record Point(int x, int y) {
    }

    public static TurComputerUseAction click(int x, int y, String button) {
        return new TurComputerUseAction(Kind.CLICK, x, y, button, List.of(), null, null, null, List.of());
    }

    public static TurComputerUseAction doubleClick(int x, int y) {
        return new TurComputerUseAction(Kind.DOUBLE_CLICK, x, y, null, List.of(), null, null, null, List.of());
    }

    public static TurComputerUseAction move(int x, int y) {
        return new TurComputerUseAction(Kind.MOVE, x, y, null, List.of(), null, null, null, List.of());
    }

    public static TurComputerUseAction scroll(int x, int y, int scrollX, int scrollY) {
        return new TurComputerUseAction(Kind.SCROLL, x, y, null, List.of(), null, scrollX, scrollY, List.of());
    }

    public static TurComputerUseAction keypress(List<String> keys) {
        return new TurComputerUseAction(Kind.KEYPRESS, null, null, null, keys, null, null, null, List.of());
    }

    public static TurComputerUseAction type(String text) {
        return new TurComputerUseAction(Kind.TYPE, null, null, null, List.of(), text, null, null, List.of());
    }

    public static TurComputerUseAction drag(List<Point> path) {
        return new TurComputerUseAction(Kind.DRAG, null, null, null, List.of(), null, null, null, path);
    }

    public static TurComputerUseAction screenshot() {
        return new TurComputerUseAction(Kind.SCREENSHOT, null, null, null, List.of(), null, null, null, List.of());
    }

    public static TurComputerUseAction waitAction() {
        return new TurComputerUseAction(Kind.WAIT, null, null, null, List.of(), null, null, null, List.of());
    }
}
