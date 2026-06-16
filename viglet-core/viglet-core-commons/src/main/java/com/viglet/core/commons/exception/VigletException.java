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

/**
 * Base <em>checked</em> exception shared across Viglet products.
 *
 * <p>The three products each hand-rolled an identical {@code Tur*}/{@code Sh*}/
 * {@code Dum*Exception}; this is the neutral home (Block Q / T375). Deliberately
 * neutral: no {@code Tur*} naming and no dependency on any product module. A
 * product keeps its own named subclass so existing {@code throws}/{@code catch}
 * sites are unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletException extends Exception {

    private static final long serialVersionUID = 1L;

    public VigletException(String message) {
        super(message);
    }

    public VigletException(Throwable cause) {
        super(cause);
    }

    public VigletException(String message, Throwable cause) {
        super(message, cause);
    }
}
