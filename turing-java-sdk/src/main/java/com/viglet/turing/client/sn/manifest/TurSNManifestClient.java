/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.turing.client.sn.manifest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import com.viglet.turing.client.sn.TurSNTimeouts;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.utils.TurSNClientUtils;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * T1030 / §XX.17 — the SDK's client for {@code POST /api/sn/manifest}: how a Java
 * connector provisions the site it is about to index from the schema it already
 * declares.
 *
 * <p>Without it a connector had no path to the endpoint, so in practice nobody
 * provisioned: fields were created at index time from the first document that
 * carried them, which infers a type from a single value and leaves an optional
 * field absent from early documents with no declaration at all. Building the
 * manifest from {@link com.viglet.turing.client.sn.job.TurSNJobAttributeSpec}s —
 * the type a connector already publishes with each job — means the declaration
 * that travels with a document is the one that provisions the site.
 *
 * <p>Typical start-up, declaring the schema and refusing to index against a
 * different one:
 *
 * <pre>{@code
 * TurSNManifest manifest = TurSNManifest.of(server, mySchema.attributeSpecs());
 * TurSNManifestClient client = new TurSNManifestClient(server);
 *
 * TurSNManifestDiff plan = client.plan(manifest);          // dry-run first
 * if (!plan.undeclaredBreakingChanges().isEmpty()) {
 *     throw new IllegalStateException("schema drift: " + plan.undeclaredBreakingChanges());
 * }
 * TurSNManifestResult result = client.provision(manifest); // idempotent
 * }</pre>
 *
 * <p>Authentication rides on the same {@link TurSNServer} credentials the
 * indexing path uses (an API key in the {@code Key} header, or basic auth).
 * {@code /api/sn/**} is CSRF-exempt server-side, so no token round-trip is
 * needed — the same reason {@code /api/sn/import} works from a headless
 * connector. The endpoint requires {@code ROLE_ADMIN} or {@code SN_CREATE},
 * which an index-only credential may not carry: that arrives as a 401/403 and is
 * thrown, not swallowed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public class TurSNManifestClient {

    private static final String MANIFEST_CONTEXT = "%s/api/sn/manifest";
    private static final String PLAN_CONTEXT = "%s/api/sn/manifest/plan";
    private static final String ERROR_KEY = "error";
    private static final String BREAKING_CHANGES_KEY = "breakingChanges";

    /**
     * Unknown properties are tolerated on the way in: the server may add a field
     * to the result or the diff, and an older connector must keep working rather
     * than failing to parse a response whose meaning it already has.
     */
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private final TurSNServer turSNServer;

    public TurSNManifestClient(TurSNServer turSNServer) {
        this.turSNServer = turSNServer;
    }

    /**
     * Converges the site and its field schema to the manifest. Idempotent:
     * re-posting an unchanged manifest returns a result whose
     * {@link TurSNManifestResult#isNoOp()} is true.
     *
     * @throws TurSNManifestException on any non-200, including the HTTP 409 a
     *                                breaking change with no declared migration
     *                                returns — see
     *                                {@link TurSNManifestException#getBreakingChanges()}
     */
    public TurSNManifestResult provision(TurSNManifest manifest) {
        String url = MANIFEST_CONTEXT.formatted(turSNServer.getServerURL());
        Response response = post(url, manifest);
        if (response.status() == 200) {
            return read(response.body(), TurSNManifestResult.class, url);
        }
        throw failure(url, response);
    }

    /**
     * Computes the diff the manifest would apply without mutating anything — the
     * dry-run a connector runs before {@link #provision}, so schema drift is a
     * reviewable plan rather than a 409 at start-up.
     *
     * @throws TurSNManifestException on any non-200
     */
    public TurSNManifestDiff plan(TurSNManifest manifest) {
        String url = PLAN_CONTEXT.formatted(turSNServer.getServerURL());
        Response response = post(url, manifest);
        if (response.status() == 200) {
            return read(response.body(), TurSNManifestDiff.class, url);
        }
        throw failure(url, response);
    }

    private Response post(String url, TurSNManifest manifest) {
        if (manifest == null || manifest.name() == null || manifest.name().isBlank()) {
            throw new TurSNManifestException(
                    "Manifest needs a site name — it is what decides create vs. converge", 400,
                    List.of());
        }
        try (CloseableHttpClient client = TurSNTimeouts.httpClientFor(turSNServer)) {
            String payload = MAPPER.writeValueAsString(manifest);
            HttpPost httpPost = new HttpPost(url);
            httpPost.setEntity(new StringEntity(payload, StandardCharsets.UTF_8));
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-type", "application/json");
            httpPost.setHeader("Accept-Encoding", StandardCharsets.UTF_8.name());
            TurSNClientUtils.authentication(httpPost, turSNServer.getCredentials(),
                    turSNServer.getApiKey());
            log.debug("Posting manifest for site '{}' to {}", manifest.name(), url);
            return client.execute(httpPost,
                    response -> new Response(response.getCode(), readBody(response)));
        } catch (IOException e) {
            throw new TurSNManifestException("Could not reach the manifest endpoint at " + url, e);
        }
    }

    private static <T> T read(String body, Class<T> type, String url) {
        try {
            return MAPPER.readValue(body, type);
        } catch (RuntimeException e) {
            throw new TurSNManifestException(
                    "Could not read the manifest response from " + url + ": " + e.getMessage(), e);
        }
    }

    /**
     * Turns a non-200 into an exception that says which failure it was. A 409
     * carries the breaking changes back verbatim, so the connector reports the
     * fields it would have rewritten rather than only that something conflicted.
     */
    private static TurSNManifestException failure(String url, Response response) {
        String message = errorMessage(response.body());
        if (response.status() == 409) {
            return new TurSNManifestException(
                    "Manifest rejected: a breaking field change is not declared. " + message,
                    409, breakingChanges(response.body()));
        }
        return new TurSNManifestException("Manifest POST to %s returned %d: %s"
                .formatted(url, response.status(), message), response.status(), List.of());
    }

    /** Pulls the server's own {@code error} sentence out of the body, falling back to the raw body. */
    private static String errorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "<no body>";
        }
        try {
            Map<?, ?> parsed = MAPPER.readValue(body, Map.class);
            Object error = parsed.get(ERROR_KEY);
            return error == null ? body : error.toString();
        } catch (RuntimeException e) {
            return body;
        }
    }

    private static List<TurSNManifestFieldChange> breakingChanges(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            var node = MAPPER.readTree(body).get(BREAKING_CHANGES_KEY);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            return MAPPER.readValue(node.toString(),
                    new TypeReference<List<TurSNManifestFieldChange>>() {
                    });
        } catch (RuntimeException e) {
            // The 409 itself is the answer; a body we cannot parse must not turn
            // a clear rejection into a parse error.
            log.debug("Could not read breakingChanges off the 409 body: {}", e.getMessage());
            return List.of();
        }
    }

    private static String readBody(ClassicHttpResponse response) {
        if (response.getEntity() == null) {
            return "";
        }
        try {
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (IOException | ParseException e) {
            return "";
        }
    }

    private record Response(int status, String body) {
    }
}
