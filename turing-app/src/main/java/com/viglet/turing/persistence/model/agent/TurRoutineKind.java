/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.agent;

/**
 * Kind of executable body a {@link TurRoutine} carries. For MVP only
 * {@link #NATIVE} ships — async wrapper around a Spring AI {@code @Tool}
 * method. {@code GROOVY} is reserved for the T48.1 follow-up that lets
 * authors paste long-running scripts directly.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurRoutineKind {
    NATIVE,
    GROOVY
}
