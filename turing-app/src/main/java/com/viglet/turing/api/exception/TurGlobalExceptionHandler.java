/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.api.exception;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.viglet.turing.resilience.TurResilienceTimeoutException;

import lombok.extern.slf4j.Slf4j;

/**
 * Central RFC 7807 ProblemDetail handler. Translates business and framework
 * exceptions into a uniform JSON shape so SDK consumers can discriminate error
 * categories on a stable {@code type} URI rather than parsing free-text
 * messages.
 *
 * <p>Each response carries a {@code correlationId} (per-request UUID) and an
 * ISO-8601 {@code timestamp}, both of which appear in server logs to make
 * cross-referencing trivial.
 *
 * <p>Authentication failures (401) remain delegated to
 * {@code TurAuthenticationEntryPoint} which short-circuits before this advice.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TurGlobalExceptionHandler {

    /**
     * Business exceptions that already carry status + type URI. The most
     * common path — covers all {@code TurApiException} subclasses with a
     * single handler.
     */
    @ExceptionHandler(TurApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(TurApiException ex) {
        ProblemDetail problem = build(ex.getStatus(), ex.getTypeUri(), ex.getMessage());
        if (ex instanceof TurProviderException tpe) {
            problem.setProperty("provider", tpe.getProvider());
        }
        logBusiness(ex.getStatus(), ex);
        return ResponseEntity.status(ex.getStatus()).body(problem);
    }

    @ExceptionHandler(TurResilienceTimeoutException.class)
    public ResponseEntity<ProblemDetail> handleResilienceTimeout(TurResilienceTimeoutException ex) {
        ProblemDetail problem = build(HttpStatus.GATEWAY_TIMEOUT, TurErrorTypes.UPSTREAM_TIMEOUT, ex.getMessage());
        problem.setProperty("kind", ex.getKind().prefix());
        // Use "providerType" not "type" — the latter clashes with ProblemDetail's RFC 7807 type URI.
        problem.setProperty("providerType", ex.getType());
        log.warn("[GlobalEx] Upstream timeout: {}.{}", ex.getKind().prefix(), ex.getType());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        ProblemDetail problem = build(HttpStatus.BAD_REQUEST, TurErrorTypes.VALIDATION,
                "Validation failed for one or more fields.");
        List<ValidationError> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new ValidationError(fe.getField(), fe.getDefaultMessage()));
        }
        problem.setProperty("errors", errors);
        log.warn("[GlobalEx] Validation failed: {} field error(s)", errors.size());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException ex) {
        ProblemDetail problem = build(HttpStatus.BAD_REQUEST, TurErrorTypes.VALIDATION,
                "Missing required parameter: " + ex.getParameterName());
        problem.setProperty("parameter", ex.getParameterName());
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable(HttpMessageNotReadableException ex) {
        ProblemDetail problem = build(HttpStatus.BAD_REQUEST, TurErrorTypes.MALFORMED_REQUEST,
                "Request body is missing or malformed.");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleUnsupportedMedia(HttpMediaTypeNotSupportedException ex) {
        ProblemDetail problem = build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, TurErrorTypes.UNSUPPORTED_MEDIA,
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(problem);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        ProblemDetail problem = build(HttpStatus.METHOD_NOT_ALLOWED, TurErrorTypes.METHOD_NOT_ALLOWED,
                ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(problem);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetail> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = build(HttpStatus.CONTENT_TOO_LARGE, TurErrorTypes.FILE_TOO_LARGE,
                "Uploaded file exceeds the configured size limit.");
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(problem);
    }

    /**
     * Missing static resources are plain 404s — log at DEBUG without the stack
     * trace so noisy hashed-asset probes (e.g. stale module-federation chunks)
     * don't pollute ERROR logs.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex) {
        ProblemDetail problem = build(HttpStatus.NOT_FOUND, TurErrorTypes.NOT_FOUND,
                "Resource not found.");
        log.debug("[GlobalEx] Static resource not found: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    /**
     * Method-level access denial (e.g., {@code @PreAuthorize}). URL-level
     * access denial is intercepted by Spring Security's filter chain before
     * this advice ever runs.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail problem = build(HttpStatus.FORBIDDEN, TurErrorTypes.FORBIDDEN, "Access denied.");
        log.warn("[GlobalEx] Access denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = build(HttpStatus.BAD_REQUEST, TurErrorTypes.INVALID_ARGUMENT, ex.getMessage());
        log.warn("[GlobalEx] Invalid argument: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Last-resort handler. Anything not matched above becomes a 500 with an
     * opaque message — we never echo internal exception details in the
     * payload, only in the correlated server log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        ProblemDetail problem = build(HttpStatus.INTERNAL_SERVER_ERROR, TurErrorTypes.INTERNAL,
                "An unexpected error occurred. See server logs (correlationId).");
        Object correlationId = problem.getProperties() != null ? problem.getProperties().get("correlationId") : null;
        log.error("[GlobalEx] Unhandled exception (correlationId={})", correlationId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    // ---- helpers ---------------------------------------------------------

    private ProblemDetail build(HttpStatus status, String typeUri, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail != null ? detail : status.getReasonPhrase());
        pd.setType(URI.create(typeUri));
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("correlationId", UUID.randomUUID().toString());
        pd.setProperty("timestamp", OffsetDateTime.now().toString());
        return pd;
    }

    private void logBusiness(HttpStatus status, TurApiException ex) {
        if (status.is5xxServerError()) {
            log.error("[GlobalEx] {} {}: {}", status.value(), ex.getClass().getSimpleName(), ex.getMessage(), ex);
        } else {
            log.warn("[GlobalEx] {} {}: {}", status.value(), ex.getClass().getSimpleName(), ex.getMessage());
        }
    }

    /** Per-field validation entry exposed inside {@code errors[]}. */
    public record ValidationError(String field, String message) {}
}
