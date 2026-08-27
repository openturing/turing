/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.api.exception;

import java.util.Collection;

import org.springframework.http.HttpStatus;

/**
 * T1046 / §XX.23 — thrown when a request is well-formed and the resource exists,
 * but the search engine bound to the site cannot serve that API. Maps to HTTP
 * 501.
 *
 * <p>501 rather than 400 or 404: nothing is wrong with the request, and the site
 * is not missing. What is missing is an implementation on the server for the
 * engine this site happens to use, which is exactly what "Not Implemented"
 * states. Answering 404 would say the site does not exist, and 400 would blame a
 * caller who did nothing wrong — the DSL layer did both before this, by returning
 * an empty {@code Optional} that the controller rendered as 404.
 *
 * <p>The message names the engines that <em>do</em> serve the API, because the
 * caller's next question is always which one to use.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurUnsupportedEngineException extends TurApiException {

    private final String engine;

    public TurUnsupportedEngineException(String api, String engine,
            Collection<String> supported) {
        super(HttpStatus.NOT_IMPLEMENTED, TurErrorTypes.UNSUPPORTED_ENGINE,
                "%s is not implemented for the '%s' search engine. Engines that serve it: %s."
                        .formatted(api, engine, String.join(", ", supported)));
        this.engine = engine;
    }

    /**
     * T1047 — the variant for one <em>clause</em> of an API the engine otherwise
     * serves: a Content AI site runs a DSL query, but a {@code knn} clause in it
     * has no expression in Content AI's query DSL.
     *
     * <p>It names no alternative engines on purpose. Which engines support a given
     * clause is what the compatibility matrix is for, and a list guessed here would
     * be wrong for exactly the clauses that differ between them.
     */
    public TurUnsupportedEngineException(String what, String engine) {
        super(HttpStatus.NOT_IMPLEMENTED, TurErrorTypes.UNSUPPORTED_ENGINE,
                "%s cannot be expressed on the '%s' search engine.".formatted(what, engine));
        this.engine = engine;
    }

    /** The engine that has no implementation, for the response body. */
    public String getEngine() {
        return engine;
    }
}
