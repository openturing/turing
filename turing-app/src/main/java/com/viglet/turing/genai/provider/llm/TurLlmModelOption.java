package com.viglet.turing.genai.provider.llm;

/**
 * A single selectable model exposed to the LLM-instance model picker.
 *
 * <p>{@code id} is the exact string that must be stored on
 * {@link com.viglet.turing.persistence.model.llm.TurLLMInstance#getModelName()}
 * (what the provider SDK sends to the vendor). {@code label} is a
 * human-friendly name for the dropdown; when the vendor API only returns an id
 * the label is just the id echoed back.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurLlmModelOption(String id, String label) {

    /** Convenience factory for the common id-only case. */
    public static TurLlmModelOption of(String id) {
        return new TurLlmModelOption(id, id);
    }
}
