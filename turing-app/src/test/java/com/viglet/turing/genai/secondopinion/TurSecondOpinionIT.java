/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.secondopinion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * T522 / §XXVIII.18 — boots the full Spring context to assert the second-opinion
 * cross-check is wired and <strong>inert by default</strong>: no critic
 * configured → disabled → no verdict (the chat path is unchanged until an admin
 * turns it on).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSecondOpinionIT extends AbstractTuringSpringIT {

    @Autowired
    private TurSecondOpinionService secondOpinionService;

    @Test
    void disabledByDefault() {
        assertThat(secondOpinionService.isEnabled()).isFalse();
        assertThat(secondOpinionService.evaluate("q", "an answer", List.of("ctx"), null)).isEmpty();
    }
}
