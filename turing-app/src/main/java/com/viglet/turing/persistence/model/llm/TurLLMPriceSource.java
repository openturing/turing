package com.viglet.turing.persistence.model.llm;

/**
 * Provenance discriminator for a {@link TurLLMPrice} row (T777). Distinguishes a
 * rate the operator entered or negotiated from one the catalog reconciler seeded
 * automatically, so the scheduled sync can refresh its own {@link #CATALOG} rows
 * without ever clobbering an operator's {@link #MANUAL} rate.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurLLMPriceSource {

    /**
     * Entered or edited by an operator (admin UI) or negotiated. Authoritative —
     * the catalog price reconciler must never overwrite a row of this source.
     */
    MANUAL,

    /**
     * Seeded and refreshed automatically by the catalog price reconciler from the
     * public model catalog's indicative {@code pricing}. Owned by the reconciler,
     * which is free to refresh it as the catalog changes.
     */
    CATALOG
}
