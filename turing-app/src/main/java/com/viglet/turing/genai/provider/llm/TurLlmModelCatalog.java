package com.viglet.turing.genai.provider.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import io.github.openviglet.modelcatalog.Kind;
import io.github.openviglet.modelcatalog.ModelCatalogClient;
import io.github.openviglet.modelcatalog.ModelEntry;

import lombok.extern.slf4j.Slf4j;

/**
 * Serves the curated model catalog per provider plugin type — the static
 * fallback for the LLM-instance model picker (T577): when a live vendor query
 * returns nothing (no API key yet, offline, or a provider without a "list
 * models" endpoint), the discovery service serves these instead.
 *
 * <p>The catalog lives in its own repository — <b>{@code openviglet/model-catalog}</b>
 * — which publishes it as a public, CORS-open JSON endpoint. Turing consumes it
 * through the official {@code io.github.openviglet:model-catalog-client} library
 * (zero runtime dependencies — JDK {@code HttpClient} + a tiny built-in JSON reader)
 * rather than a hand-rolled HTTP+JSON fetch, so URL selection, {@code vendors}-map
 * flattening and the typed {@link ModelEntry}/{@link Kind} model come from the shared
 * client. Fetches from {@code turing.model-catalog.url} (default the published rolling
 * endpoint) on a TTL, so a running instance picks up new models without a redeploy.
 *
 * <p>There is <b>no bundled offline fallback</b>: the catalog is remote-only. The
 * effective map is empty until the first successful refresh, after which it holds
 * the last-good remote result (a failed fetch keeps the current map, never an
 * empty one). The refresh runs on a scheduler, off the boot path, so startup
 * never waits on the network; with {@code remote-enabled=false} the catalog stays
 * empty and callers fall back to live vendor listing only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurLlmModelCatalog {

    /**
     * The effective catalog served to callers. Empty until the first successful
     * remote refresh; thereafter it holds the last-good remote map (a failed fetch
     * keeps the current map, never an empty one from a failed request).
     */
    private volatile Map<String, List<TurLlmModelOption>> catalog = Map.of();

    /**
     * Per-vendor official pricing pages (T778), keyed by catalog vendor slug —
     * the authoritative "verify at vendor" links behind the indicative catalog
     * prices. Sourced from the client {@code providers()} registry
     * ({@code providers.json}) on the same refresh. Empty until the first
     * successful refresh; a failed fetch keeps the last-good map.
     */
    private volatile Map<String, String> providerPricingUrls = Map.of();

    /**
     * Last-good parsed catalog change feed (T788) — the added/removed/changed
     * deltas from {@code changes.json}. {@link TurCatalogChanges#EMPTY} until the
     * first successful refresh; a failed fetch keeps the last-good value.
     */
    private volatile TurCatalogChanges catalogChanges = TurCatalogChanges.EMPTY;

    /**
     * Last-good parsed consumer-plan reference (T789) from {@code plans.json}.
     * {@link TurCatalogPlans#EMPTY} until the first successful refresh; a failed
     * fetch keeps the last-good value.
     */
    private volatile TurCatalogPlans consumerPlans = TurCatalogPlans.EMPTY;

    /** Lazily built from the configured base URL; overridable for tests. */
    private volatile ModelCatalogClient client;

    @Value("${turing.model-catalog.url:https://openviglet.github.io/model-catalog/catalog.json}")
    private String remoteUrl;

    @Value("${turing.model-catalog.remote-enabled:true}")
    private boolean remoteEnabled;

    /**
     * Refreshes the effective catalog from the public endpoint on a TTL. This is
     * the only source (remote-only). Best-effort: on any failure the current map
     * is kept. Runs off the boot path (an initial delay) so startup never waits on
     * the network.
     */
    @Scheduled(
            initialDelayString = "${turing.model-catalog.refresh-initial-delay-ms:8000}",
            fixedDelayString = "${turing.model-catalog.refresh-interval-ms:21600000}")
    void refreshFromRemote() {
        if (!remoteEnabled || !StringUtils.hasText(remoteUrl)) {
            return;
        }
        refreshFrom(client());
    }

    /**
     * Refreshes the served map from the given client — force-fetches the catalog,
     * maps it to the vendor→models shape, and swaps it in. Best-effort: on any
     * failure (offline, unreachable, malformed) or an empty result, the current
     * (last-good) map is kept, never replaced with an empty one.
     */
    void refreshFrom(ModelCatalogClient modelCatalogClient) {
        try {
            List<ModelEntry> entries = modelCatalogClient.refresh().entries();
            Map<String, List<TurLlmModelOption>> parsed = toCatalogMap(entries);
            if (!parsed.isEmpty()) {
                this.catalog = parsed;
                log.info("Refreshed LLM model catalog from {} ({} providers)", remoteUrl, parsed.size());
            }
        } catch (Exception e) {
            // Offline / unreachable / malformed — keep the current (last-good) catalog.
            log.debug("Remote LLM model catalog refresh from {} failed, keeping current: {}",
                    remoteUrl, e.getMessage());
        }
        refreshProviderLinks(modelCatalogClient);
        refreshCatalogChanges(modelCatalogClient);
        refreshConsumerPlans(modelCatalogClient);
    }

    /**
     * Best-effort refresh of the consumer-plan reference (T789) from the client
     * {@code plans()} registry ({@code plans.json}). On any failure or an empty
     * result the last-good value is kept.
     */
    void refreshConsumerPlans(ModelCatalogClient modelCatalogClient) {
        try {
            TurCatalogPlans parsed = toConsumerPlans(modelCatalogClient.plans());
            if (!parsed.byVendor().isEmpty()) {
                this.consumerPlans = parsed;
            }
        } catch (Exception e) {
            log.debug("Consumer-plan reference refresh failed, keeping current: {}", e.getMessage());
        }
    }

    /**
     * Maps the raw {@code plans.json} object (untyped in the 1.0.5 client) to
     * {@link TurCatalogPlans} ({@code {plans:{vendor:[...]}}}). Tolerant of the
     * untyped shape: missing/oddly-typed nodes are skipped, never thrown.
     */
    @SuppressWarnings("unchecked")
    TurCatalogPlans toConsumerPlans(Map<String, Object> raw) {
        if (raw == null || !(raw.get("plans") instanceof Map<?, ?> plansNode)) {
            return TurCatalogPlans.EMPTY;
        }
        Map<String, List<TurCatalogPlans.TurCatalogPlan>> byVendor = new LinkedHashMap<>();
        for (Map.Entry<?, ?> vendorEntry : plansNode.entrySet()) {
            if (!(vendorEntry.getKey() instanceof String vendor)
                    || !(vendorEntry.getValue() instanceof List<?> list)) {
                continue;
            }
            List<TurCatalogPlans.TurCatalogPlan> plans = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> planMap) {
                    plans.add(toPlan(vendor, (Map<String, Object>) planMap));
                }
            }
            if (!plans.isEmpty()) {
                byVendor.put(vendor, List.copyOf(plans));
            }
        }
        return byVendor.isEmpty() ? TurCatalogPlans.EMPTY : new TurCatalogPlans(Map.copyOf(byVendor));
    }

    private TurCatalogPlans.TurCatalogPlan toPlan(String vendor, Map<String, Object> m) {
        return new TurCatalogPlans.TurCatalogPlan(
                vendor,
                str(m.get("id")),
                str(m.get("name")),
                str(m.get("product")),
                str(m.get("tier")),
                dbl(m.get("priceMonthlyUSD")),
                dbl(m.get("annualMonthlyUSD")),
                str(m.get("currency")),
                str(m.get("url")),
                m.get("indicative") instanceof Boolean b ? b : null);
    }

    private static Double dbl(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    /**
     * Best-effort refresh of the catalog change feed (T788) from the client
     * {@code changes()} registry ({@code changes.json}). On any failure or a feed
     * with no entries the last-good value is kept.
     */
    void refreshCatalogChanges(ModelCatalogClient modelCatalogClient) {
        try {
            TurCatalogChanges parsed = toCatalogChanges(modelCatalogClient.changes());
            if (!parsed.added().isEmpty() || !parsed.removed().isEmpty() || !parsed.changed().isEmpty()) {
                this.catalogChanges = parsed;
            }
        } catch (Exception e) {
            log.debug("Catalog change-feed refresh failed, keeping current: {}", e.getMessage());
        }
    }

    /**
     * Maps the raw {@code changes.json} object (untyped in the 1.0.5 client) to
     * {@link TurCatalogChanges}. Tolerant of the untyped shape: a missing/oddly
     * typed list yields an empty list rather than throwing.
     */
    TurCatalogChanges toCatalogChanges(Map<String, Object> raw) {
        if (raw == null) {
            return TurCatalogChanges.EMPTY;
        }
        return new TurCatalogChanges(
                changeEntries(raw.get("added")),
                changeEntries(raw.get("removed")),
                changeEntries(raw.get("changed")));
    }

    @SuppressWarnings("unchecked")
    private List<TurCatalogChanges.TurCatalogChangeEntry> changeEntries(Object listValue) {
        if (!(listValue instanceof List<?> list)) {
            return List.of();
        }
        List<TurCatalogChanges.TurCatalogChangeEntry> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> entry) {
                Map<String, Object> m = (Map<String, Object>) entry;
                String id = str(m.get("id"));
                if (id != null && !id.isBlank()) {
                    out.add(new TurCatalogChanges.TurCatalogChangeEntry(
                            str(m.get("vendor")), id, str(m.get("kind")), str(m.get("label")), str(m.get("detail"))));
                }
            }
        }
        return List.copyOf(out);
    }

    private static String str(Object value) {
        return value instanceof String s ? s : null;
    }

    /**
     * Best-effort refresh of the per-vendor official pricing pages (T778) from the
     * client {@code providers()} registry. The registry is
     * {@code {providers:[{catalogVendor, apiPricingUrl, ...}]}}; each provider with
     * both a {@code catalogVendor} and an {@code apiPricingUrl} contributes one
     * entry. On any failure or an empty result the last-good map is kept.
     */
    void refreshProviderLinks(ModelCatalogClient modelCatalogClient) {
        try {
            Map<String, String> parsed = toProviderPricingUrls(modelCatalogClient.providers());
            if (!parsed.isEmpty()) {
                this.providerPricingUrls = parsed;
            }
        } catch (Exception e) {
            log.debug("Provider pricing-links refresh failed, keeping current: {}", e.getMessage());
        }
    }

    /**
     * Maps the raw {@code providers.json} object (untyped in the 1.0.5 client) to a
     * {@code catalogVendor → apiPricingUrl} map. Tolerant of the untyped shape:
     * anything that is not the expected list-of-maps is skipped, never thrown.
     */
    @SuppressWarnings("unchecked")
    Map<String, String> toProviderPricingUrls(Map<String, Object> raw) {
        if (raw == null) {
            return Map.of();
        }
        Object providers = raw.get("providers");
        if (!(providers instanceof List<?> list)) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> provider)) {
                continue;
            }
            Object vendor = ((Map<String, Object>) provider).get("catalogVendor");
            Object url = ((Map<String, Object>) provider).get("apiPricingUrl");
            if (vendor instanceof String v && !v.isBlank() && url instanceof String u && !u.isBlank()) {
                out.put(v.toLowerCase(Locale.ROOT), u);
            }
        }
        return Map.copyOf(out);
    }

    /**
     * Maps the client's flattened {@link ModelEntry} list into the vendor→models map
     * the picker serves. The catalog {@link Kind} is <b>trusted directly</b> when known
     * (only a {@code null}/{@link Kind#UNKNOWN UNKNOWN} kind falls back to the id/label
     * heuristic), and the rich {@link ModelEntry} fields (pricing, limits, capabilities,
     * modalities, benchmarks, performance, cutoff, tier…) are carried through as
     * {@link TurLlmModelMetadata} (T776) instead of being discarded. Vendors preserve
     * first-seen order; blank ids are skipped.
     */
    Map<String, List<TurLlmModelOption>> toCatalogMap(List<ModelEntry> entries) {
        Map<String, List<TurLlmModelOption>> byVendor = new LinkedHashMap<>();
        for (ModelEntry entry : entries) {
            String id = entry.id();
            if (id == null || id.isBlank()) {
                continue;
            }
            String vendor = entry.vendor() == null ? "" : entry.vendor().toLowerCase(Locale.ROOT);
            String label = entry.label() != null ? entry.label() : id;
            Kind kind = entry.kind();
            String override = (kind == null || kind == Kind.UNKNOWN) ? null : kind.name();
            byVendor.computeIfAbsent(vendor, v -> new ArrayList<>())
                    .add(new TurLlmModelOption(id, label,
                            TurLlmModelKind.parseOrClassify(override, id, label),
                            TurLlmModelMetadata.fromCatalog(entry)));
        }
        Map<String, List<TurLlmModelOption>> out = new LinkedHashMap<>();
        byVendor.forEach((vendor, models) -> out.put(vendor, List.copyOf(models)));
        return Map.copyOf(out);
    }

    /** The catalog client, built once from the configured base URL. */
    ModelCatalogClient client() {
        ModelCatalogClient current = this.client;
        if (current == null) {
            current = ModelCatalogClient.builder()
                    .baseUrl(catalogBaseUrl())
                    .timeout(Duration.ofSeconds(5))
                    .build();
            this.client = current;
        }
        return current;
    }

    /** Inject a client (built with a fake fetcher) for tests. */
    void useClient(ModelCatalogClient modelCatalogClient) {
        this.client = modelCatalogClient;
    }

    /**
     * The catalog client wants the endpoint <em>base</em> (it appends the file it
     * needs). {@code turing.model-catalog.url} may be configured either as that base
     * or as a full URL to a specific {@code *.json} file (its historical default);
     * a trailing JSON filename is stripped so both forms work.
     */
    String catalogBaseUrl() {
        String url = remoteUrl.trim();
        int slash = url.lastIndexOf('/');
        if (slash > "https://".length() && url.substring(slash + 1).endsWith(".json")) {
            return url.substring(0, slash);
        }
        return url;
    }

    /** Curated models for a plugin type, or empty when none are available yet. */
    public List<TurLlmModelOption> staticModels(String pluginType) {
        if (pluginType == null) {
            return List.of();
        }
        return catalog.getOrDefault(pluginType.toLowerCase(Locale.ROOT), List.of());
    }

    /**
     * The full effective catalog as an immutable vendor→models map — the source
     * the catalog price reconciler (T777) and other cross-vendor consumers read
     * to walk every known model and its {@link TurLlmModelMetadata}. Empty until
     * the first successful remote refresh.
     */
    public Map<String, List<TurLlmModelOption>> allModels() {
        return catalog;
    }

    /**
     * Per-vendor official "verify at vendor" pricing-page URLs (T778), keyed by
     * lower-cased catalog vendor slug. Empty until the first successful refresh.
     */
    public Map<String, String> providerPricingUrls() {
        return providerPricingUrls;
    }

    /**
     * The last-good parsed catalog change feed (T788) — added/removed/changed
     * model deltas since the previous catalog snapshot. {@link TurCatalogChanges#EMPTY}
     * until the first successful refresh.
     */
    public TurCatalogChanges catalogChanges() {
        return catalogChanges;
    }

    /**
     * The last-good consumer-plan reference (T789) — indicative consumer
     * subscription plans per vendor. {@link TurCatalogPlans#EMPTY} until the first
     * successful refresh.
     */
    public TurCatalogPlans consumerPlans() {
        return consumerPlans;
    }
}
