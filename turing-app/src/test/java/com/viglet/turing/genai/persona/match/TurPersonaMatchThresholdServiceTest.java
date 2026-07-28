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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchProject;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchProjectRepository;

/**
 * T826 / §LXI.2 (Block BN) — the per-project good-fit threshold resolves to one
 * number: the project's own when it set a sane one, the deployment default
 * otherwise. The default-path assertions are the no-behaviour-change guarantee
 * (an unconfigured deployment still judges at 75, exactly as the old
 * {@code GOOD_FIT_THRESHOLD} literal did).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurPersonaMatchThresholdServiceTest {

    @Mock
    private TurPersonaMatchProjectRepository projectRepository;

    @InjectMocks
    private TurPersonaMatchThresholdService service;

    private static TurPersonaMatchProject project(Double threshold) {
        TurPersonaMatchProject p = new TurPersonaMatchProject();
        p.setId("p1");
        p.setFitThreshold(threshold);
        return p;
    }

    @Test
    void unsetProjectThresholdInheritsThePlatformDefault() {
        assertThat(service.resolve(null))
                .isEqualTo(TurPersonaMatchThresholdService.DEFAULT_FIT_THRESHOLD);
        assertThat(service.globalDefault()).isEqualTo(75.0);
    }

    @Test
    void projectThresholdWinsOverTheDefault() {
        assertThat(service.resolve(60.0)).isEqualTo(60.0);
        assertThat(service.resolve(85.5)).isEqualTo(85.5);
    }

    @Test
    void outOfRangeThresholdFallsBackToTheDefault() {
        assertThat(service.resolve(0.0)).isEqualTo(75.0);
        assertThat(service.resolve(-10.0)).isEqualTo(75.0);
        assertThat(service.resolve(140.0)).isEqualTo(75.0);
    }

    @Test
    void aBadConfiguredDefaultStillYieldsAUsableBar() {
        ReflectionTestUtils.setField(service, "defaultThreshold", 0.0);
        assertThat(service.globalDefault()).isEqualTo(75.0);
        ReflectionTestUtils.setField(service, "defaultThreshold", 65.0);
        assertThat(service.globalDefault()).isEqualTo(65.0);
        // A deployment-wide bar is inherited by every project that names none.
        assertThat(service.resolve(null)).isEqualTo(65.0);
    }

    @Test
    void forProjectReadsTheProjectsOwnBar() {
        when(projectRepository.findById("p1")).thenReturn(Optional.of(project(55.0)));
        assertThat(service.forProject("p1")).isEqualTo(55.0);
    }

    @Test
    void forProjectFallsBackToTheDefaultForAnUnknownOrThresholdlessProject() {
        when(projectRepository.findById("gone")).thenReturn(Optional.empty());
        assertThat(service.forProject("gone")).isEqualTo(75.0);
        assertThat(service.forProject((String) null)).isEqualTo(75.0);
        assertThat(service.forProject(project(null))).isEqualTo(75.0);
        assertThat(service.forProject((TurPersonaMatchProject) null)).isEqualTo(75.0);
    }

    @Test
    void sanitizeKeepsSaneValuesAndNullsTheRest() {
        assertThat(service.sanitize(60.0)).isEqualTo(60.0);
        assertThat(service.sanitize(82.456)).isEqualTo(82.46); // rounded to 2 decimals
        assertThat(service.sanitize(null)).isNull();
        assertThat(service.sanitize(0.0)).isNull();
        assertThat(service.sanitize(120.0)).isNull();
    }
}
