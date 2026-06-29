/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.clienttool;

import java.util.List;

import com.viglet.turing.persistence.model.agent.TurAIAgent;

/**
 * A source of <em>built-in</em> frontend ("client") tools that {@link
 * TurClientToolService} folds into an agent's advertised tool set when the agent
 * opts in via a per-agent flag.
 *
 * <p>Built-ins differ from operator-declared client tools ({@code clientToolsJson})
 * in that their names, descriptions and JSON schemas ship with the product and a
 * matching frontend renderer/handler is provided by the SDK. Each provider is
 * gated by its own flag (e.g. answer-as-app, co-browse) so capabilities compose
 * independently. They park + resume through the very same T438 protocol.
 *
 * <p>Implementations are Spring beans; the service injects all of them and
 * de-duplicates by tool name (an operator declaration of the same name wins).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurBuiltInClientToolProvider {

    /** Whether this provider's tools should be advertised for the given agent. */
    boolean appliesTo(TurAIAgent agent);

    /**
     * The built-in client-tool declarations this provider contributes for the
     * given agent. Static providers ignore {@code agent} and return a fixed list;
     * dynamic ones (e.g. skill-UI, T449) vary by the agent's configuration.
     */
    List<TurClientTool> declarations(TurAIAgent agent);
}
