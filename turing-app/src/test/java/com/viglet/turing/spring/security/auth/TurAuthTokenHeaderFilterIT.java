/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.spring.security.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.viglet.turing.persistence.model.dev.token.TurDevToken;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * T1061 — an SN import authenticated only by a developer token in the {@code Key}
 * header, with no session cookie, the way Dumont and the Java SDK call it.
 *
 * <p>The token filter used to walk the lazy role → privileges collection with no
 * session open, so the request failed with HTTP 500
 * ({@code LazyInitializationException}) before it reached the controller.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.jmx.enabled=true",
                "turing.permissions=true"
        })
class TurAuthTokenHeaderFilterIT extends AbstractTuringSpringIT {

    // One item for a site that does not exist: the import is queued and the
    // listener drops it, so the test exercises auth without indexing anything.
    private static final String IMPORT_BODY = """
            {"turingDocuments":[{"turSNJobAction":"CREATE","siteNames":["t1061-no-such-site"],\
            "locale":"en_US","attributes":{"id":"t1061"}}]}""";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private TurDevTokenRepository turDevTokenRepository;

    private HttpResponse<String> importWith(String key) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/sn/import"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(IMPORT_BODY));
        if (key != null) {
            builder.header(TurAuthTokenHeaderFilter.KEY, key);
        }
        return HttpClient.newHttpClient().send(builder.build(), BodyHandlers.ofString());
    }

    @Test
    void importWithOnlyKeyHeaderIsAccepted() throws Exception {
        // Saved with no security context, so auditing stamps createdBy = "admin",
        // the seeded user whose groups → roles → privileges the filter resolves.
        TurDevToken token = new TurDevToken();
        token.setTitle("T1061 key-only import");
        token.setToken("t1061-" + UUID.randomUUID());
        turDevTokenRepository.save(token);

        HttpResponse<String> response = importWith(token.getToken());

        assertEquals(200, response.statusCode(),
                () -> "key-only import must authenticate; got " + response.statusCode()
                        + " body=" + response.body());
        assertEquals("true", response.body());
    }

    @Test
    void importWithoutKeyIsRejected() throws Exception {
        HttpResponse<String> response = importWith(null);

        assertEquals(401, response.statusCode(),
                () -> "unauthenticated import must be rejected; got " + response.statusCode());
    }
}
