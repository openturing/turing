/*
 * Copyright (C) 2016-2023 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.spring.security;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * Client Silos isolation gate (Viglet Cloud C051). Enforces, at login time, that
 * the authenticated user's {@code viglet_client} claim matches the client this
 * instance is dedicated to (see {@code turing.required-viglet-client}).
 *
 * <p>This closes the browser (OAuth2 authorization-code) hole that the nginx
 * bearer-token gate cannot cover: browser navigation carries an app session
 * cookie, not a JWT, so a non-member could otherwise complete an interactive
 * login at another client's dedicated host. Rejecting here denies that login.
 *
 * <p>No-op when the required client is blank/unset (a shared instance).
 *
 * @since 2026.1
 */
public final class TurClientClaimGate {

    public static final String VIGLET_CLIENT_CLAIM = "viglet_client";

    private TurClientClaimGate() {
    }

    /**
     * @param requiredClient the client this instance is restricted to (blank = no restriction)
     * @param actualClaim    the {@code viglet_client} claim from the token (may be null)
     * @throws OAuth2AuthenticationException (access_denied) when a restriction is set and the claim does not match
     */
    public static void enforce(String requiredClient, Object actualClaim) {
        if (requiredClient == null || requiredClient.isBlank()) {
            return;
        }
        String actual = actualClaim == null ? null : actualClaim.toString();
        if (!requiredClient.equals(actual)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(
                            OAuth2ErrorCodes.ACCESS_DENIED,
                            "This instance is restricted to the '" + requiredClient + "' client.",
                            null),
                    "viglet_client claim '" + actual + "' does not match required '" + requiredClient + "'");
        }
    }
}
