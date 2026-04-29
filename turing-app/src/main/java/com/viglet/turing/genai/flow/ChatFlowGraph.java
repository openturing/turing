/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Deserialized form of {@code TurChatFlow.definitionJson}. Mirrors the React
 * Flow payload produced by the front-end editor: a list of typed nodes plus
 * the directed edges connecting them.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.5
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatFlowGraph(List<ChatFlowNode> nodes, List<ChatFlowEdge> edges) {

    public ChatFlowGraph {
        nodes = nodes == null ? List.of() : nodes;
        edges = edges == null ? List.of() : edges;
    }

    /**
     * @return the {@code start} node, or empty if the graph has no Start node.
     */
    public Optional<ChatFlowNode> startNode() {
        return nodes.stream()
                .filter(n -> "start".equals(n.type()))
                .findFirst();
    }

    /** @return the node with the given id, or empty if not present. */
    public Optional<ChatFlowNode> nodeById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return nodes.stream()
                .filter(n -> id.equals(n.id()))
                .findFirst();
    }

    /**
     * Outgoing edges of {@code nodeId}, in the order they appear in the
     * payload. The first one is treated as the default branch by the engine.
     */
    public List<ChatFlowEdge> outgoingEdges(String nodeId) {
        if (nodeId == null) {
            return List.of();
        }
        return edges.stream()
                .filter(e -> nodeId.equals(e.source()))
                .toList();
    }
}
