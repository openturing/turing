/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.provider.store.lucene;

import java.util.Locale;
import java.util.Optional;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104HnswScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;

/**
 * T514 / §XXVIII.10 — opt-in scalar quantization for the embedded Lucene KNN
 * vector store. Storing quantized vectors instead of full {@code float32}
 * shrinks the index on disk and speeds KNN, at a measured recall cost the
 * operator accepts per store.
 *
 * <p>Each mode maps to a Lucene 10.4 {@link ScalarEncoding} used by a
 * {@link Lucene104HnswScalarQuantizedVectorsFormat} on the {@code embedding}
 * field:
 * <ul>
 *   <li>{@link #NONE} — full {@code float32} (the default; legacy behaviour).</li>
 *   <li>{@link #INT8} — 1 byte/component (≈4× smaller), the standard accuracy
 *       win with negligible recall loss.</li>
 *   <li>{@link #INT4} — packed nibble, ≈8× smaller, larger recall trade-off.</li>
 *   <li>{@link #BINARY} — 1 bit/component, the most aggressive shrink for very
 *       large stores where speed/size dominate recall.</li>
 * </ul>
 *
 * <p>Pairs naturally with Matryoshka dimensionality truncation (the embedding
 * model's {@code outputDimensionality} option) — fewer dimensions × fewer bits
 * per dimension compound the store shrink. The scalar-quantized format caps the
 * <em>advertised</em> dimension limit at 1024, so the per-field wrapper raises it
 * to {@link #MAX_QUANTIZED_DIMENSIONS} to keep 1536/3072-dim models (OpenAI,
 * Gemini) working when not truncated.
 *
 * <p><b>Operational note:</b> changing the mode only affects newly written
 * segments — Lucene records the codec per segment, so old float segments stay
 * readable and a full re-index converts everything to the new format.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurLuceneQuantization {

    NONE(null),
    INT8(ScalarEncoding.UNSIGNED_BYTE),
    INT4(ScalarEncoding.PACKED_NIBBLE),
    SEVEN_BIT(ScalarEncoding.SEVEN_BIT),
    BINARY(ScalarEncoding.SINGLE_BIT_QUERY_NIBBLE);

    /** HNSW graph defaults mirroring Lucene's own ({@code maxConn=16}, {@code beamWidth=100}). */
    private static final int HNSW_MAX_CONN = 16;
    private static final int HNSW_BEAM_WIDTH = 100;
    /** Raised dimension ceiling so quantization works with 1536/3072-dim models. */
    static final int MAX_QUANTIZED_DIMENSIONS = 4096;

    private final ScalarEncoding encoding;

    TurLuceneQuantization(ScalarEncoding encoding) {
        this.encoding = encoding;
    }

    /**
     * Parses a provider-option string ({@code none}/{@code int8}/{@code int4}/
     * {@code seven_bit}/{@code binary}, case-insensitive) into a mode, defaulting
     * to {@link #NONE} for null/blank/unrecognized values so a typo never breaks
     * indexing.
     */
    public static TurLuceneQuantization fromOption(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    /**
     * Builds a {@link Codec} that quantizes the {@code embedding} KNN field with
     * this mode's encoding (delegating everything else to the default codec), or
     * {@link Optional#empty()} for {@link #NONE} (use the default codec untouched).
     */
    public Optional<Codec> buildCodec() {
        if (encoding == null) {
            return Optional.empty();
        }
        KnnVectorsFormat quantized =
                new Lucene104HnswScalarQuantizedVectorsFormat(encoding, HNSW_MAX_CONN, HNSW_BEAM_WIDTH);
        KnnVectorsFormat perField = new PerFieldKnnVectorsFormat() {
            @Override
            public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
                return quantized;
            }

            @Override
            public int getMaxDimensions(String field) {
                return MAX_QUANTIZED_DIMENSIONS;
            }
        };
        Codec base = Codec.getDefault();
        return Optional.of(new FilterCodec(base.getName(), base) {
            @Override
            public KnnVectorsFormat knnVectorsFormat() {
                return perField;
            }
        });
    }
}
