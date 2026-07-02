/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.genai.multimodal;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;

import com.viglet.turing.genai.multimodal.TurMultimodalRetrievalService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T511 / §XXVIII.7 — REST surface for multimodal retrieval: index an image into
 * the shared text/image vector space and search images with a plain-text query.
 *
 * <p>Both endpoints resolve a multimodal embedding model (Voyage
 * {@code voyage-multimodal-3} today). When {@code embeddingModelId} /
 * {@code storeInstanceId} are omitted, the platform Global Settings defaults are
 * used. The capability is opt-in: with a text-only embedding model configured,
 * indexing returns {@code indexed=false} and search returns an empty list rather
 * than failing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/v2/multimodal")
@Tag(name = "Multimodal Retrieval", description = "Search text → match an image (T511)")
public class TurMultimodalRetrievalAPI {

    private final TurMultimodalRetrievalService service;

    public TurMultimodalRetrievalAPI(TurMultimodalRetrievalService service) {
        this.service = service;
    }

    /** Whether the resolved embedding model supports image embedding. */
    @Operation(summary = "Check if multimodal (image) embedding is available")
    @GetMapping("/available")
    @Secured({ "ROLE_ADMIN", "STORE_VIEW" })
    public AvailabilityResponse available(
            @RequestParam(required = false) String embeddingModelId,
            @RequestParam(required = false) String storeInstanceId) {
        return new AvailabilityResponse(service.isAvailable(embeddingModelId, storeInstanceId));
    }

    /** Embeds an uploaded image and upserts its vector, tagged as an image. */
    @Operation(summary = "Index an image into the shared text/image vector space")
    @PostMapping(path = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Secured({ "ROLE_ADMIN", "STORE_EDIT" })
    public IndexResponse indexImage(
            @RequestParam(required = false) String embeddingModelId,
            @RequestParam(required = false) String storeInstanceId,
            @RequestParam String collection,
            @RequestParam String id,
            @RequestParam(required = false) String label,
            @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        try {
            Map<String, Object> metadata = label == null ? Map.of() : Map.of("label", label);
            boolean indexed = service.indexImage(embeddingModelId, storeInstanceId, collection,
                    id, file.getBytes(), file.getContentType(), metadata);
            return new IndexResponse(id, indexed);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Could not read uploaded image: " + e.getMessage());
        }
    }

    /** Searches images by a plain-text query. */
    @Operation(summary = "Search images by a text query")
    @GetMapping("/search")
    @Secured({ "ROLE_ADMIN", "STORE_VIEW" })
    public List<ImageHit> search(
            @RequestParam(required = false) String embeddingModelId,
            @RequestParam(required = false) String storeInstanceId,
            @RequestParam String collection,
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "10") int topK) {
        return service.searchImagesByText(embeddingModelId, storeInstanceId, collection, query, topK)
                .stream()
                .map(TurMultimodalRetrievalAPI::toHit)
                .toList();
    }

    private static ImageHit toHit(Document doc) {
        return new ImageHit(doc.getId(), doc.getScore(),
                doc.getMetadata() == null ? Map.of() : doc.getMetadata());
    }

    /** Whether image embedding is available with the resolved model. */
    public record AvailabilityResponse(boolean available) {
    }

    /** Outcome of an image index request. */
    public record IndexResponse(String id, boolean indexed) {
    }

    /** One image match for a text query. */
    public record ImageHit(String id, Double score, Map<String, Object> metadata) {
    }
}
