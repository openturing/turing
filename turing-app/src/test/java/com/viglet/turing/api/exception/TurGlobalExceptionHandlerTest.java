/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.api.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.resilience.TurResilienceRegistry;
import com.viglet.turing.resilience.TurResilienceTimeoutException;

class TurGlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = standaloneSetup(new TestController())
                .setControllerAdvice(new TurGlobalExceptionHandler())
                .build();
    }

    @Test
    void notFound_mapsTo404WithProblemDetail() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.title", equalTo("Not Found")))
                .andExpect(jsonPath("$.detail", containsString("LLM instance")))
                .andExpect(jsonPath("$.type", startsWith("tag:viglet.com,2026:errors/not-found")))
                .andExpect(jsonPath("$.correlationId", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void validation_mapsTo400() throws Exception {
        mvc.perform(get("/test/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.type", startsWith("tag:viglet.com,2026:errors/validation")));
    }

    @Test
    void conflict_mapsTo409() throws Exception {
        mvc.perform(delete("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", equalTo(409)))
                .andExpect(jsonPath("$.type", startsWith("tag:viglet.com,2026:errors/conflict")));
    }

    @Test
    void forbidden_mapsTo403() throws Exception {
        mvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type", startsWith("tag:viglet.com,2026:errors/access-denied")));
    }

    @Test
    void provider_mapsTo502AndKeepsProviderField() throws Exception {
        mvc.perform(get("/test/provider"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status", equalTo(502)))
                .andExpect(jsonPath("$.provider", equalTo("openai")))
                .andExpect(jsonPath("$.type", startsWith("tag:viglet.com,2026:errors/provider-failure")));
    }

    @Test
    void storage_mapsTo502() throws Exception {
        mvc.perform(get("/test/storage"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.type", startsWith("tag:viglet.com,2026:errors/storage-failure")));
    }

    @Test
    void resilienceTimeout_mapsTo504WithKindAndProviderType() throws Exception {
        mvc.perform(get("/test/timeout"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.status", equalTo(504)))
                .andExpect(jsonPath("$.kind", equalTo("llm")))
                .andExpect(jsonPath("$.providerType", equalTo("openai")))
                .andExpect(jsonPath("$.type", startsWith("tag:viglet.com,2026:errors/upstream-timeout")));
    }

    @Test
    void illegalArgument_mapsTo400() throws Exception {
        mvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", equalTo("must be positive")))
                .andExpect(jsonPath("$.type", startsWith("tag:viglet.com,2026:errors/invalid-argument")));
    }

    @Test
    void missingParameter_mapsTo400WithParameterName() throws Exception {
        mvc.perform(get("/test/needs-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.parameter", equalTo("siteId")));
    }

    @Test
    void methodNotAllowed_mapsTo405() throws Exception {
        mvc.perform(post("/test/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type", startsWith("tag:viglet.com,2026:errors/method-not-allowed")));
    }

    @Test
    void unexpectedException_mapsTo500WithOpaqueMessage() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail", containsString("unexpected error")))
                .andExpect(jsonPath("$.type", startsWith("tag:viglet.com,2026:errors/internal")));
    }

    @Test
    void responseContentTypeIsApplicationProblemJson() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(content -> {
                    String contentType = content.getResponse().getContentType();
                    if (contentType == null || !contentType.contains("application/problem+json")) {
                        throw new AssertionError("Expected RFC 7807 content-type, got " + contentType);
                    }
                });
    }

    @Test
    void notFoundResponseHasNoErrorsArrayWhenNotValidation() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void unknownResource_mapsTo500FromUnhandledNullPointer() throws Exception {
        // NullPointerException isn't explicitly handled; falls through to the
        // generic Exception handler so we never expose stack traces in the body.
        mvc.perform(get("/test/npe"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(containsString("NullPointerException"))));
    }

    @Test
    void validationException_carriesValidationTypeUri() throws Exception {
        mvc.perform(get("/test/business-validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", startsWith("tag:viglet.com,2026:errors/validation")))
                .andExpect(jsonPath("$.detail", equalTo("name must be unique")));
    }

    /**
     * SSE client disconnect — the handler must NOT try to write a
     * ProblemDetail body (would fail with HttpMessageNotWritableException
     * because the response is locked to text/event-stream). With the
     * dedicated {@code handleClientDisconnect} returning void, MockMvc
     * sees a 200 with no body and the request finishes cleanly. The
     * regression we're pinning: without the dedicated handler, this
     * call would fail with a SECOND exception (HttpMessageNotWritable)
     * inside the generic 500 handler — exactly the cascading-error log
     * we saw in production.
     */
    @Test
    void clientDisconnect_isSwallowedWithoutSecondException() throws Exception {
        mvc.perform(get("/test/sse-aborted"))
                .andExpect(status().isOk())
                // No response body — the SSE stream is gone, no point
                // sending anything else. The dedicated handler returns void.
                .andExpect(content -> {
                    String body = content.getResponse().getContentAsString();
                    if (body != null && !body.isEmpty()) {
                        throw new AssertionError("Expected empty body, got: " + body);
                    }
                });
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/not-found")
        public void notFound() {
            throw TurNotFoundException.of("LLM instance", "abc-123");
        }

        @GetMapping("/validation")
        public void validation() {
            throw new TurValidationException("invalid payload");
        }

        @GetMapping("/business-validation")
        public void businessValidation() {
            throw new TurValidationException("name must be unique");
        }

        @org.springframework.web.bind.annotation.DeleteMapping("/conflict")
        public void conflict() {
            throw new TurConflictException("core in use");
        }

        @GetMapping("/forbidden")
        public void forbidden() {
            throw new TurForbiddenException("read-only catalog");
        }

        @GetMapping("/provider")
        public void provider() {
            throw new TurProviderException("openai", "model unavailable");
        }

        @GetMapping("/storage")
        public void storage() {
            throw new TurStorageException("bucket unreachable");
        }

        @GetMapping("/timeout")
        public void timeout() {
            throw new TurResilienceTimeoutException(
                    TurResilienceRegistry.Kind.LLM,
                    "openai",
                    new java.util.concurrent.TimeoutException("60s"));
        }

        @GetMapping("/illegal-argument")
        public void illegalArgument() {
            throw new IllegalArgumentException("must be positive");
        }

        @GetMapping("/needs-param")
        public void needsParam(@RequestParam String siteId) {
            // never reached without param
        }

        @GetMapping("/boom")
        public void boom() {
            throw new RuntimeException("unhandled internal failure");
        }

        @GetMapping("/npe")
        public void npe() {
            String s = null;
            s.length();
        }

        @GetMapping("/sse-aborted")
        public void sseAborted() throws org.springframework.web.context.request.async.AsyncRequestNotUsableException {
            // Simulates an SSE stream interrupted by a client disconnect.
            // Real-world this comes from ResponseBodyEmitterReturnValueHandler
            // wrapping a Tomcat ClientAbortException when the visitor closes
            // the tab during the stream. The dedicated handler should
            // swallow it without firing the generic 500 path.
            throw new org.springframework.web.context.request.async.AsyncRequestNotUsableException(
                    "ServletResponse failed to flushBuffer: simulated client disconnect");
        }
    }
}
