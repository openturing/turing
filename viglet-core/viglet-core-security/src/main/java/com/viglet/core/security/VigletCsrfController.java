/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.security;

import java.util.Map;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the current {@link CsrfToken} so a SPA can prime its CSRF header
 * before issuing its first mutating request (the {@code XSRF-TOKEN} cookie is
 * typically {@code HttpOnly}, so JavaScript reads the token from here instead).
 *
 * <p>Lifted from Turing's {@code TurCsrfController} into {@code viglet-core}
 * (Block Q / T369). The path is configurable via
 * {@code viglet.security.csrf.endpoint} (default {@code /api/csrf}) so each
 * product keeps its own URL. Registered by {@link VigletSecurityAutoConfiguration}
 * only when the consumer hasn't supplied its own {@code VigletCsrfController}
 * bean, so a product subclass (e.g. Turing's) wins.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
public class VigletCsrfController {

    @GetMapping("${viglet.security.csrf.endpoint:/api/csrf}")
    public Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName());
    }
}
