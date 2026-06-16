/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.core.commons.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VigletExceptionTest {

    @Test
    void checkedExceptionCarriesMessageAndCause() {
        VigletException ex = new VigletException("boom");
        assertThat(ex).isInstanceOf(Exception.class).hasMessage("boom");

        Throwable cause = new IllegalStateException("inner");
        assertThat(new VigletException(cause)).hasCause(cause);
        assertThat(new VigletException("wrap", cause)).hasMessage("wrap").hasCause(cause);
    }

    @Test
    void runtimeExceptionCarriesMessageAndCause() {
        VigletRuntimeException ex = new VigletRuntimeException("boom");
        assertThat(ex).isInstanceOf(RuntimeException.class).hasMessage("boom");

        Throwable cause = new IllegalStateException("inner");
        assertThat(new VigletRuntimeException(cause)).hasCause(cause);
        assertThat(new VigletRuntimeException("wrap", cause)).hasMessage("wrap").hasCause(cause);
    }
}
