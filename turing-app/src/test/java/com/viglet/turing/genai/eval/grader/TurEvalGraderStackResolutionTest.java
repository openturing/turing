/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.eval.grader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.eval.grader.builtin.TurNodeGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurOutcomeGrader;
import com.viglet.turing.genai.eval.grader.builtin.TurSlotMatchGrader;
import com.viglet.turing.persistence.model.agent.TurAgentEvalCase;
import com.viglet.turing.persistence.model.agent.TurAgentEvalSet;
import com.viglet.turing.persistence.model.agent.TurEvalGraderConfig;
import com.viglet.turing.persistence.model.agent.TurEvalGraderStack;
import com.viglet.turing.persistence.repository.agent.TurEvalGraderStackRepository;

/**
 * T600 / §XXXIII.15 — a set bound to a reusable stack resolves the stack's
 * graders instead of the set's own.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurEvalGraderStackResolutionTest {

    @Mock
    private TurEvalGraderStackRepository stackRepository;

    private TurEvalGraderRegistry registry() {
        return new TurEvalGraderRegistry(
                List.of(new TurSlotMatchGrader(), new TurOutcomeGrader(), new TurNodeGrader()),
                stackRepository);
    }

    private static TurEvalGraderConfig config(String graderId) {
        TurEvalGraderConfig c = new TurEvalGraderConfig();
        c.setGraderId(graderId);
        c.setEnabled(1);
        return c;
    }

    @Test
    void boundStackOverridesTheSetsOwnConfigs() {
        TurEvalGraderStack stack = new TurEvalGraderStack();
        stack.setId("stack-1");
        stack.getConfigs().add(config(TurEvalBuiltinGraders.NODE));
        when(stackRepository.findById("stack-1")).thenReturn(Optional.of(stack));

        TurAgentEvalSet set = new TurAgentEvalSet();
        set.setGraderStackId("stack-1");
        // The set also has its own config, which the bound stack must override.
        set.getGraderConfigs().add(config(TurEvalBuiltinGraders.SLOT_MATCH));

        assertThat(registry().resolveStack(set, new TurAgentEvalCase()))
                .extracting(rg -> rg.grader().graderId())
                .containsExactly(TurEvalBuiltinGraders.NODE);
    }
}
