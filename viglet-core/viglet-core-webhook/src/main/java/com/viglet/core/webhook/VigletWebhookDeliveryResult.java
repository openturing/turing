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

/**
 * Outcome of a {@link VigletWebhookDispatcher} delivery after the retry loop
 * has run.
 *
 * @param delivered  true when a 2xx response was received
 * @param statusCode last HTTP status seen ({@code 0} if no response — connect/timeout/IO error)
 * @param attempts   number of attempts actually made
 * @param error      human-readable failure reason when {@code !delivered}, else {@code null}
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record VigletWebhookDeliveryResult(boolean delivered, int statusCode, int attempts, String error) {

    public static VigletWebhookDeliveryResult ok(int statusCode, int attempts) {
        return new VigletWebhookDeliveryResult(true, statusCode, attempts, null);
    }

    public static VigletWebhookDeliveryResult failed(int statusCode, int attempts, String error) {
        return new VigletWebhookDeliveryResult(false, statusCode, attempts, error);
    }
}
