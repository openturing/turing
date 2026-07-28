/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.match;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchProject;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchProjectRepository;

/**
 * The one place that answers "what counts as a good fit for this project?"
 * (T826 / §LXI.2, Block BN).
 *
 * <p>Before T826 the answer was a {@code GOOD_FIT_THRESHOLD = 75.0} literal in
 * {@link TurPersonaMatchHistoryService} mirrored by the studio's colour bands —
 * one number, duplicated across the stack, deciding the heatmap's green/amber/red,
 * the drift "crossed below fit" flag and (with T827/T828) who gets alerted and
 * which builds fail. Different portfolios have genuinely different bars, so the
 * project owns it: a nullable {@code fitThreshold} where {@code null} inherits the
 * deployment default ({@code turing.persona-match.fit-threshold}, 75) — which
 * keeps every existing project's behaviour byte-for-byte.
 *
 * <p>Deliberately per <em>project</em>, not per cell or per persona: a project is
 * the unit an operator reasons about, and a per-pair bar would make the heatmap
 * unreadable.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurPersonaMatchThresholdService {

    /**
     * The platform's historical bar, kept as the default of the configurable
     * default so an unconfigured deployment behaves exactly as it did pre-T826.
     */
    public static final double DEFAULT_FIT_THRESHOLD = 75.0;

    /** A threshold outside this range is meaningless for a 0-100 fit score. */
    private static final double MIN_THRESHOLD = 1.0;
    private static final double MAX_THRESHOLD = 100.0;

    private final TurPersonaMatchProjectRepository projectRepository;

    /** Deployment-wide fallback for a project that names no threshold of its own. */
    @Value("${turing.persona-match.fit-threshold:75}")
    private double defaultThreshold = DEFAULT_FIT_THRESHOLD;

    public TurPersonaMatchThresholdService(
            TurPersonaMatchProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    /**
     * The effective good-fit bar for one project — its own value when it set one,
     * otherwise the deployment default. A project id that does not resolve yields
     * the default rather than an error: a threshold read must never be the reason a
     * drift report or a run fails.
     */
    public double forProject(String projectId) {
        if (projectId == null) {
            return globalDefault();
        }
        return resolve(projectRepository.findById(projectId)
                .map(TurPersonaMatchProject::getFitThreshold)
                .orElse(null));
    }

    /** The effective bar for an already-loaded project. */
    public double forProject(TurPersonaMatchProject project) {
        return resolve(project == null ? null : project.getFitThreshold());
    }

    /**
     * Resolve a raw (possibly null / out-of-range) per-project value against the
     * deployment default.
     */
    public double resolve(Double projectThreshold) {
        if (projectThreshold == null || !inRange(projectThreshold)) {
            return globalDefault();
        }
        return projectThreshold;
    }

    /** The deployment default, itself clamped so a bad property cannot break bands. */
    public double globalDefault() {
        return inRange(defaultThreshold) ? defaultThreshold : DEFAULT_FIT_THRESHOLD;
    }

    /**
     * What to persist for a threshold coming off the wire: {@code null} (inherit)
     * for anything absent or out of range, so a bogus value degrades to the
     * default instead of silently colouring the whole matrix red.
     */
    public Double sanitize(Double raw) {
        return raw != null && inRange(raw) ? round(raw) : null;
    }

    private static boolean inRange(double value) {
        return value >= MIN_THRESHOLD && value <= MAX_THRESHOLD;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
