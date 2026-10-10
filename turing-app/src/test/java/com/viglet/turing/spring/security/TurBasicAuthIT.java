/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.spring.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * T1063 — a script calls an admin API with HTTP Basic credentials of a local
 * user, with no session and no XSRF round-trip.
 *
 * <p>Reported as "basic auth answers 401 though httpBasic is enabled". The 401s
 * were {@code BadCredentialsException}s: {@code TURING_ADMIN_PASSWORD} only sets
 * the password while none is stored, so on a reused database the env value is
 * ignored at startup and is not the admin's password.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jmx.enabled=true",
                "turing.admin.password=" + TurBasicAuthIT.PASSWORD
        })
class TurBasicAuthIT extends AbstractTuringSpringIT {

    static final String PASSWORD = "t1063-basic-auth-pw";

    @Value("${local.server.port}")
    private int port;

    private HttpResponse<String> getSites(String user, String password) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/sn")).GET();
        if (user != null) {
            String credentials = Base64.getEncoder()
                    .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + credentials);
        }
        return HttpClient.newHttpClient().send(builder.build(), BodyHandlers.ofString());
    }

    @Test
    void basicAuthWithTheLocalAdminPasswordIsAccepted() throws Exception {
        HttpResponse<String> response = getSites("admin", PASSWORD);

        assertEquals(200, response.statusCode(),
                () -> "basic auth as the local admin must authenticate; got " + response.statusCode()
                        + " body=" + response.body());
    }

    @Test
    void basicAuthWithAWrongPasswordIsRejected() throws Exception {
        HttpResponse<String> response = getSites("admin", "not-the-password");

        assertEquals(401, response.statusCode());
    }

    @Test
    void noCredentialsIsRejected() throws Exception {
        HttpResponse<String> response = getSites(null, null);

        assertEquals(401, response.statusCode());
    }
}
