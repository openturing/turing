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
package com.viglet.core.webhook;

import java.time.Duration;

/**
 * Per-attempt response timeout and the bounded retry/back-off applied by
 * {@link VigletWebhookDispatcher} to a single webhook delivery.
 *
 * <p>Best-effort, no dead-letter queue: after {@code maxAttempts} the delivery
 * is dropped (logged by the caller). The back-off is <b>linear</b> — the dispatcher
 * sleeps {@code retryBackoff * attempt} between attempts, matching the established
 * product behaviour ({@code Shio}'s {@code retryBackoffMillis * attempt}).
 *
 * @param maxAttempts   total attempts before giving up (floored at 1)
 * @param readTimeout   per-attempt response timeout
 * @param retryBackoff  base linear back-off, multiplied by the (1-based) attempt number
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record VigletWebhookRetryPolicy(int maxAttempts, Duration readTimeout, Duration retryBackoff) {

    public VigletWebhookRetryPolicy {
        if (maxAttempts < 1) {
            maxAttempts = 1;
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            readTimeout = Duration.ofSeconds(5);
        }
        if (retryBackoff == null || retryBackoff.isNegative()) {
            retryBackoff = Duration.ZERO;
        }
    }

    /** Sensible defaults: 3 attempts, 5s read timeout, 2s linear back-off. */
    public static VigletWebhookRetryPolicy defaults() {
        return new VigletWebhookRetryPolicy(3, Duration.ofSeconds(5), Duration.ofSeconds(2));
    }

    /** A single-attempt policy (no retry) with the given read timeout. */
    public static VigletWebhookRetryPolicy once(Duration readTimeout) {
        return new VigletWebhookRetryPolicy(1, readTimeout, Duration.ZERO);
    }

    /** Back-off before the given 1-based attempt number (attempt 1 = no wait). */
    Duration backoffBefore(int attempt) {
        if (attempt <= 1 || retryBackoff.isZero()) {
            return Duration.ZERO;
        }
        return retryBackoff.multipliedBy(attempt - 1L);
    }
}
