package com.viglet.turing.genai.provider.llm;

import java.util.List;

/**
 * A parsed snapshot of the model catalog's change feed (T788) — the
 * {@code added}/{@code removed}/{@code changed} deltas the public catalog
 * publishes at {@code changes.json} between refreshes. Sourced from the
 * {@code model-catalog-client}'s untyped {@code changes()} registry and narrowed
 * to the fields Turing acts on, so the change-notification service reads a stable
 * shape instead of raw maps.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurCatalogChanges(
        List<TurCatalogChangeEntry> added,
        List<TurCatalogChangeEntry> removed,
        List<TurCatalogChangeEntry> changed) {

    /** Empty feed — the served value until the first successful refresh. */
    public static final TurCatalogChanges EMPTY = new TurCatalogChanges(List.of(), List.of(), List.of());

    /** One model entry in a change list. {@code detail} is a free-text note for {@code changed} rows. */
    public record TurCatalogChangeEntry(String vendor, String id, String kind, String label, String detail) {
    }
}
