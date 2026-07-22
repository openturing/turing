package com.viglet.turing.genai.provider.llm;

import java.util.List;
import java.util.Map;

/**
 * Parsed consumer-subscription-plan reference from the model catalog (T789) — the
 * indicative consumer plans (Claude Pro, ChatGPT Plus…) the public catalog
 * publishes at {@code plans.json}, keyed by vendor. Sourced from the
 * {@code model-catalog-client}'s untyped {@code plans()} registry and narrowed to
 * the fields the admin-help reference shows. Reference only — indicative US list
 * prices, always verify at the vendor.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurCatalogPlans(Map<String, List<TurCatalogPlan>> byVendor) {

    /** Empty reference — the served value until the first successful refresh. */
    public static final TurCatalogPlans EMPTY = new TurCatalogPlans(Map.of());

    /** One consumer subscription plan. */
    public record TurCatalogPlan(
            String vendor,
            String id,
            String name,
            String product,
            String tier,
            Double priceMonthlyUsd,
            Double annualMonthlyUsd,
            String currency,
            String url,
            Boolean indicative) {
    }
}
