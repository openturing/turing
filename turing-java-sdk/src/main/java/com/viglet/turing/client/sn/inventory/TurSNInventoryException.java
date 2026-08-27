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
package com.viglet.turing.client.sn.inventory;

import java.io.Serial;

/**
 * T1035 / §XX.20 — the inventory could not be read.
 *
 * <p>Thrown rather than returned as an empty page, for the same reason
 * {@link TurSNInventoryPage#supported()} exists: an audit that mistakes "could
 * not read the index" for "the index holds nothing" concludes every document is
 * missing and republishes the whole source. A failure has to be loud.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurSNInventoryException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** HTTP status the server returned, or {@code -1} when the call never completed. */
    private final int statusCode;

    public TurSNInventoryException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public TurSNInventoryException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int getStatusCode() {
        return statusCode;
    }

    /**
     * True when the site itself was not found — a misconfigured site name, not a
     * transient failure, so retrying will not help.
     */
    public boolean isSiteNotFound() {
        return statusCode == 404;
    }
}
