/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.transcription;

/**
 * T849 / §LXIII.4 — receives transcript text <em>as it is produced</em>, for a
 * backend that can stream a completed file's transcription
 * ({@code gpt-transcribe} with {@code stream=true}). The counterpart of
 * {@link TurTranscriptionProgressListener}: that one reports <em>how much</em> is
 * done, this one reports <em>what was said</em>.
 *
 * <p>Only the <b>single-chunk</b> path emits partials. Chunks are transcribed
 * bounded-parallel (T689), so interleaving deltas from concurrent chunks would
 * produce text in the wrong order — for a chunked recording the honest signal
 * remains the chunk-index progress. That is also exactly the gap this closes: a
 * single-chunk recording has no sub-progress at all today, so its UI can only
 * show an indeterminate bar.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@FunctionalInterface
public interface TurTranscriptionPartialListener {

    /** Discards partials — what every caller that doesn't render text passes. */
    TurTranscriptionPartialListener NOOP = text -> {
        // no-op
    };

    /**
     * Called with the transcript accumulated <b>so far</b> (not just the delta),
     * so a consumer can render it without keeping its own buffer. Never called
     * with a blank value, and never called after the result is returned.
     *
     * <p>Implementations must be cheap and must not throw — this runs on the
     * transcription worker while the HTTP response is still being read.
     */
    void onPartialText(String accumulatedText);
}
