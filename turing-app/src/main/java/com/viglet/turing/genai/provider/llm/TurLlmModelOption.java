package com.viglet.turing.genai.provider.llm;

/**
 * A single selectable model exposed to the LLM-instance model picker.
 *
 * <p>{@code id} is the exact string that must be stored on
 * {@link com.viglet.turing.persistence.model.llm.TurLLMInstance}'s
 * {@code modelName} (what the provider SDK sends to the vendor). {@code label} is a
 * human-friendly name for the dropdown; when the vendor API only returns an id
 * the label is just the id echoed back. {@code kind} is the classified
 * {@link TurLlmModelKind model kind} (T750) so the picker can badge and filter
 * by type; it is derived from {@code id} + {@code label} unless supplied.
 *
 * <p>{@code metadata} (T776) carries the rich, catalog-sourced facts (pricing,
 * context/output limits, capabilities, modalities, benchmarks, performance,
 * knowledge cutoff, tier…) recovered from the {@code model-catalog-client}'s
 * {@link io.github.openviglet.modelcatalog.ModelEntry}. It is {@code null} for a
 * model discovered by a live vendor listing (no catalog entry) and may be
 * {@code null} for a catalog entry that carries only {@code id/label/kind}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurLlmModelOption(String id, String label, TurLlmModelKind kind, TurLlmModelMetadata metadata) {

    /**
     * {@code (id, label, kind)} form without catalog metadata — used by live vendor
     * listings where only the kind is derivable. Carries a {@code null} metadata.
     */
    public TurLlmModelOption(String id, String label, TurLlmModelKind kind) {
        this(id, label, kind, null);
    }

    /**
     * Backward-compatible {@code (id, label)} form: classifies the
     * {@link TurLlmModelKind kind} from the id + label heuristically. Existing
     * call sites (live vendor listings, the catalog loader) use this and gain a
     * kind for free.
     */
    public TurLlmModelOption(String id, String label) {
        this(id, label, TurLlmModelKind.classify(id, label));
    }

    /** Convenience factory for the common id-only case. */
    public static TurLlmModelOption of(String id) {
        return new TurLlmModelOption(id, id);
    }
}
