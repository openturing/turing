/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.system;

import java.io.PrintStream;

import lombok.extern.slf4j.Slf4j;

/**
 * Reports a misconfiguration that stops startup, on a channel that survives the
 * logging system.
 *
 * <p>T986 / §XCV — writing to {@code System.err} from application code is
 * ordinarily wrong, and it is deliberate here. A configuration error thrown from
 * an {@code ApplicationRunner} in the fat jar can end the process with exit 1 and
 * a log that stops mid-sentence: the appenders are not always in a state to render
 * a throwable, and the operator gets a return code with no cause. Rediscovering
 * the reason meant re-running with
 * {@code -Dorg.springframework.boot.logging.LoggingSystem=none}, which is not a
 * move anyone knows to make in the field.
 *
 * <p>So the message goes to both places: the log, because that is where it belongs
 * when logging works, and stderr, because that is where it arrives when logging
 * does not. Use this only for failures that stop the process — a banner is loud on
 * purpose and would be noise for anything recoverable.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public final class TurStartupFailure {

    private static final String RULE = "=".repeat(78);
    private static final String HEADING = "Viglet Turing ES cannot start";

    private TurStartupFailure() {
        // utility
    }

    /**
     * Reports {@code message} to the log and to stderr, then returns the exception
     * for the caller to throw. Returning rather than throwing keeps the {@code throw}
     * at the call site, where the compiler can still see the method ends there.
     *
     * @param message what is wrong and which setting fixes it, in the operator's terms
     * @return the exception to throw
     */
    public static IllegalStateException fatal(String message) {
        log.error("{}: {}", HEADING, message);
        banner(System.err, message);
        return new IllegalStateException(message);
    }

    /** Visible for testing: the same banner, on a stream the test can read back. */
    static void banner(PrintStream out, String message) {
        out.println();
        out.println(RULE);
        out.println(HEADING);
        out.println();
        out.println(message);
        out.println(RULE);
        out.flush();
    }
}
