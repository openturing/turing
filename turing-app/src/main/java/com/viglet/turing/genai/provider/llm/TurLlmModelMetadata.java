package com.viglet.turing.genai.provider.llm;

import java.util.List;

import io.github.openviglet.modelcatalog.Benchmarks;
import io.github.openviglet.modelcatalog.Classification;
import io.github.openviglet.modelcatalog.Classifier;
import io.github.openviglet.modelcatalog.Modalities;
import io.github.openviglet.modelcatalog.ModelEntry;
import io.github.openviglet.modelcatalog.Performance;
import io.github.openviglet.modelcatalog.Pricing;

/**
 * The rich, catalog-sourced metadata carried alongside a {@link TurLlmModelOption}
 * (T776). Until this block the {@code model-catalog-client} fetched a full
 * {@link ModelEntry} per model but {@link TurLlmModelCatalog#toCatalogMap} collapsed
 * every entry to {@code id/label/kind} and threw the rest away, forcing every
 * downstream feature (pricing, context-window entry, the native-capability matrix)
 * to re-enter facts the catalog already states. This record widens that funnel once:
 * it preserves pricing, context/output limits, embedding dimensions, capabilities,
 * modalities, benchmarks, performance, knowledge cutoff, release date,
 * deprecation/status, and the client {@link Classifier}'s tier/tags so the picker
 * and services can read the catalog instead of re-deriving it.
 *
 * <p>Every field is nullable — a model surfaced by a live vendor listing (no catalog
 * entry) carries a {@code null} metadata on its option; a catalog entry may still
 * omit any individual field. Consumers must treat absence as "unknown", never as a
 * default value (a missing price is not {@code $0}, a missing context window is not
 * {@code 0}). The nested records mirror the catalog client's own
 * {@link Pricing}/{@link Benchmarks}/{@link Performance}/{@link Modalities} shapes so
 * the JSON served to the frontend is stable and self-describing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurLlmModelMetadata(
        Integer contextWindow,
        Integer maxOutputTokens,
        Integer embeddingDimensions,
        List<String> capabilities,
        TurLlmModelModalities modalities,
        TurLlmModelPricing pricing,
        TurLlmModelBenchmarks benchmarks,
        TurLlmModelPerformance performance,
        String knowledgeCutoff,
        String releaseDate,
        Boolean deprecated,
        String status,
        String tier,
        List<String> tags) {

    /** Per-1M-token indicative pricing, mirrored from catalog {@link Pricing}. */
    public record TurLlmModelPricing(
            Double inputPer1M,
            Double outputPer1M,
            String currency,
            Boolean indicative,
            String source,
            String lastVerified) {
    }

    /** Quality signals, mirrored from catalog {@link Benchmarks}. */
    public record TurLlmModelBenchmarks(Double intelligenceIndex, Double arenaElo) {
    }

    /** Runtime signals, mirrored from catalog {@link Performance}. */
    public record TurLlmModelPerformance(Double throughputTps, Double latencyTtftSec) {
    }

    /** Supported input/output modalities, mirrored from catalog {@link Modalities}. */
    public record TurLlmModelModalities(List<String> input, List<String> output) {
    }

    /**
     * Maps a catalog {@link ModelEntry} (and the client {@link Classifier}'s tier/tags)
     * into the carrier. Returns {@code null} when there is nothing worth carrying, so a
     * bare {@code id/label/kind} entry keeps a {@code null} metadata rather than an
     * empty shell. Copies list fields defensively (immutable copies).
     *
     * @param entry the catalog entry (never {@code null})
     * @return the metadata, or {@code null} when the entry carries no extra fields
     */
    static TurLlmModelMetadata fromCatalog(ModelEntry entry) {
        Classification classification = safeClassify(entry);
        TurLlmModelModalities modalities = toModalities(entry.modalities());
        TurLlmModelPricing pricing = toPricing(entry.pricing());
        TurLlmModelBenchmarks benchmarks = toBenchmarks(entry.benchmarks());
        TurLlmModelPerformance performance = toPerformance(entry.performance());
        List<String> capabilities = copyOrNull(entry.capabilities());
        List<String> tags = classification == null ? null : copyOrNull(classification.tags());
        String tier = classification == null ? null : classification.tier();

        boolean empty = entry.contextWindow() == null
                && entry.maxOutputTokens() == null
                && entry.embeddingDimensions() == null
                && capabilities == null
                && modalities == null
                && pricing == null
                && benchmarks == null
                && performance == null
                && entry.knowledgeCutoff() == null
                && entry.releaseDate() == null
                && entry.deprecated() == null
                && entry.status() == null
                && tier == null
                && tags == null;
        if (empty) {
            return null;
        }
        return new TurLlmModelMetadata(
                entry.contextWindow(),
                entry.maxOutputTokens(),
                entry.embeddingDimensions(),
                capabilities,
                modalities,
                pricing,
                benchmarks,
                performance,
                entry.knowledgeCutoff(),
                entry.releaseDate(),
                entry.deprecated(),
                entry.status(),
                tier,
                tags);
    }

    private static Classification safeClassify(ModelEntry entry) {
        try {
            return Classifier.classify(entry);
        } catch (RuntimeException e) {
            // The client classifier is best-effort; a malformed entry must not drop
            // the whole model from the catalog. Carry the other fields without a tier.
            return null;
        }
    }

    private static TurLlmModelModalities toModalities(Modalities m) {
        if (m == null || (isEmpty(m.input()) && isEmpty(m.output()))) {
            return null;
        }
        return new TurLlmModelModalities(copyOrNull(m.input()), copyOrNull(m.output()));
    }

    private static TurLlmModelPricing toPricing(Pricing p) {
        if (p == null || (p.inputPer1M() == null && p.outputPer1M() == null)) {
            return null;
        }
        return new TurLlmModelPricing(
                p.inputPer1M(), p.outputPer1M(), p.currency(), p.indicative(), p.source(), p.lastVerified());
    }

    private static TurLlmModelBenchmarks toBenchmarks(Benchmarks b) {
        if (b == null || (b.intelligenceIndex() == null && b.arenaElo() == null)) {
            return null;
        }
        return new TurLlmModelBenchmarks(b.intelligenceIndex(), b.arenaElo());
    }

    private static TurLlmModelPerformance toPerformance(Performance perf) {
        if (perf == null || (perf.throughputTps() == null && perf.latencyTtftSec() == null)) {
            return null;
        }
        return new TurLlmModelPerformance(perf.throughputTps(), perf.latencyTtftSec());
    }

    private static List<String> copyOrNull(List<String> list) {
        return isEmpty(list) ? null : List.copyOf(list);
    }

    private static boolean isEmpty(List<String> list) {
        return list == null || list.isEmpty();
    }
}
