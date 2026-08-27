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

/**
 * T1035 / §XX.20 — one document as the index holds it: the identity an audit
 * compares against its own source.
 *
 * @param id           the document id, exactly as the connector published it —
 *                     so both sides join on it without translation
 * @param type         the indexed content type, for bucketing differences on a
 *                     mixed site; not part of the comparison
 * @param version      epoch milliseconds, normalised by the server across every
 *                     engine's date representation. {@code null} when the
 *                     document carries none of the version fields
 * @param versionField which field the version came from ({@code
 *                     modification_date} or {@code publication_date}), so a
 *                     caller knows what its own timestamp is being compared to
 * @param checksum     the source-computed content checksum the connector
 *                     published (T1034). {@code null} for a document indexed
 *                     before that, or by a connector that computes none — which
 *                     is why the version is still there
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurSNInventoryEntry(String id, String type, Long version, String versionField,
        String checksum) {

    /**
     * True when this row and {@code otherChecksum} disagree about content.
     *
     * <p>Answers {@code false} when either side has no checksum: "unknown" is not
     * "different", and treating it as different would have an audit republish
     * every document a connector cannot checksum.
     */
    public boolean checksumDiffers(String otherChecksum) {
        return checksum != null && otherChecksum != null && !checksum.equals(otherChecksum);
    }

    /**
     * True when this row's version is older than {@code sourceEpochMs} — the
     * source has changed since the index was written.
     *
     * <p>Answers {@code false} when either side has no version, for the reason
     * above. Prefer {@link #checksumDiffers} when both sides have a checksum: a
     * timestamp a source rewrites without changing content reports drift that is
     * not there.
     */
    public boolean staleAgainst(Long sourceEpochMs) {
        return version != null && sourceEpochMs != null && version < sourceEpochMs;
    }
}
