/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.sn.kb;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * T671 / §XL (Block AQ) — a compact, immutable-after-{@link #build()}
 * Aho-Corasick automaton for multi-pattern substring search in a single pass
 * over the text. It is generic over the payload {@code <P>} carried by each
 * pattern so the recognition dictionary can attach the resolved canonical term
 * to every surface form.
 *
 * <p>Patterns and text are expected to already be folded to the same normal form
 * (see {@link TurTextNormalizer}); word-boundary and per-variation case/accent
 * verification are the caller's responsibility ({@link TurRecognitionDictionary})
 * because they need the original (unfolded) text. Standard goto/fail/output
 * construction; matching is O(text length + matches).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
final class TurAhoCorasick<P> {

    /** A single hit: the payload plus the [start, end) span in the searched text. */
    record Hit<P>(P payload, int start, int end) {
    }

    private static final class Node<P> {
        final Map<Character, Integer> next = new HashMap<>();
        int fail = 0;
        final List<PatternRef<P>> outputs = new ArrayList<>();
    }

    private record PatternRef<P>(int length, P payload) {
    }

    private final List<Node<P>> nodes = new ArrayList<>();
    private boolean built = false;

    TurAhoCorasick() {
        nodes.add(new Node<>()); // root
    }

    void add(String pattern, P payload) {
        if (built) {
            throw new IllegalStateException("Cannot add patterns after build()");
        }
        if (pattern == null || pattern.isEmpty()) {
            return;
        }
        int node = 0;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            Integer child = nodes.get(node).next.get(c);
            if (child == null) {
                child = nodes.size();
                nodes.add(new Node<>());
                nodes.get(node).next.put(c, child);
            }
            node = child;
        }
        nodes.get(node).outputs.add(new PatternRef<>(pattern.length(), payload));
    }

    /** Wires the fail links (BFS). Safe to call multiple times; only the first builds. */
    void build() {
        if (built) {
            return;
        }
        Deque<Integer> queue = new ArrayDeque<>();
        Node<P> root = nodes.get(0);
        for (int child : root.next.values()) {
            nodes.get(child).fail = 0;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            int current = queue.poll();
            Node<P> currentNode = nodes.get(current);
            for (Map.Entry<Character, Integer> e : currentNode.next.entrySet()) {
                char c = e.getKey();
                int child = e.getValue();
                int fail = currentNode.fail;
                while (fail != 0 && !nodes.get(fail).next.containsKey(c)) {
                    fail = nodes.get(fail).fail;
                }
                Integer failNext = nodes.get(fail).next.get(c);
                int childFail = (failNext != null && failNext != child) ? failNext : 0;
                nodes.get(child).fail = childFail;
                nodes.get(child).outputs.addAll(nodes.get(childFail).outputs);
                queue.add(child);
            }
        }
        built = true;
    }

    /** Runs the automaton over {@code text}, returning every pattern hit. */
    List<Hit<P>> search(String text) {
        build();
        List<Hit<P>> hits = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return hits;
        }
        int node = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            while (node != 0 && !nodes.get(node).next.containsKey(c)) {
                node = nodes.get(node).fail;
            }
            Integer nextNode = nodes.get(node).next.get(c);
            node = nextNode != null ? nextNode : 0;
            for (PatternRef<P> ref : nodes.get(node).outputs) {
                int end = i + 1;
                hits.add(new Hit<>(ref.payload(), end - ref.length(), end));
            }
        }
        return hits;
    }
}
