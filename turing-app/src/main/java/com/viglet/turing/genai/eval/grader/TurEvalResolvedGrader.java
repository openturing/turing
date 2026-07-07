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

/**
 * T588 / §XXXIII.3 — one resolved stack entry: a grader bean paired with the
 * config it should run under. Produced by {@link
 * TurEvalGraderRegistry#resolveStack}; the runner calls {@code
 * grader.grade(ctx, config)}.
 *
 * @param grader the resolved grader bean
 * @param config its effective config (a default view for the legacy stack)
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEvalResolvedGrader(TurEvalGrader grader, TurEvalGraderConfigView config) {
}
