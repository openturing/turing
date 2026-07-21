package com.viglet.turing.genai.provider.llm;

import java.util.Collection;
import java.util.Locale;

import org.springframework.util.StringUtils;

/**
 * The kind of task a model performs, used to make the LLM-instance model picker
 * type-aware (T750). A vendor's {@code /v1/models} returns every kind mixed
 * together; classifying each id lets the picker badge and filter them so an
 * operator never picks an embedding, image or transcription model as a chat
 * default by mistake.
 *
 * <p>Vision is deliberately <b>not</b> a kind: a multimodal chat model reading
 * an image is still {@link #CHAT}; image <i>understanding</i> is modelled by the
 * per-instance {@code nativeCapabilities} matrix (F.15), not here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurLlmModelKind {

    /** Text / conversational generation (the "LLM"). Default for unrecognised ids. */
    CHAT,
    /** Text → vector (RAG / semantic search). */
    EMBEDDING,
    /** Reorders candidates by relevance (Cohere / Voyage rerank). */
    RERANK,
    /** Text → image (DALL·E / gpt-image / Imagen / Titan Image). */
    IMAGE,
    /** Audio → text (Whisper / gpt-4o-transcribe). */
    TRANSCRIPTION,
    /** Text → audio / TTS (tts-1 / gpt-4o-mini-tts). */
    SPEECH,
    /** Text → video (Sora / Veo). */
    VIDEO,
    /** Safety classification (omni-moderation). */
    MODERATION,
    /** Blank / unparseable id — reserved so it never swallows a real model. */
    UNKNOWN;

    private static final String KW_EMBED = "embed";

    /**
     * Pure heuristic classifier over a model {@code id} + {@code label}. Order
     * matters: {@code rerank} is checked before {@code embed} (Cohere/Voyage
     * rerank ids also carry the vendor family), and the media/moderation keywords
     * before the {@link #CHAT} default. Anything that matches no specialised
     * keyword is {@link #CHAT} (most listed models are chat and have no keyword);
     * {@link #UNKNOWN} is reserved for a blank / null id so it stays honest.
     *
     * @param id    the model id (the string sent to the vendor)
     * @param label the human-friendly label (may be {@code null})
     */
    public static TurLlmModelKind classify(String id, String label) {
        if (!StringUtils.hasText(id)) {
            return UNKNOWN;
        }
        String lid = id.toLowerCase(Locale.ROOT);
        String hay = (lid + " " + (label == null ? "" : label)).toLowerCase(Locale.ROOT);
        if (hay.contains("rerank")) {
            return RERANK;
        }
        if (hay.contains(KW_EMBED) || lid.startsWith("voyage-")) {
            return EMBEDDING;
        }
        if (hay.contains("moderation")) {
            return MODERATION;
        }
        if (hay.contains("dall-e") || hay.contains("dall·e") || hay.contains("imagen")
                || hay.contains("image")) {
            return IMAGE;
        }
        if (hay.contains("whisper") || hay.contains("transcribe") || hay.contains("transcription")) {
            return TRANSCRIPTION;
        }
        if (hay.contains("tts") || hay.contains("text-to-speech") || hay.contains("-speech")) {
            return SPEECH;
        }
        if (hay.contains("sora") || hay.contains("-veo") || lid.startsWith("veo") || hay.contains("video")) {
            return VIDEO;
        }
        return CHAT;
    }

    /**
     * Resolves a catalog entry's kind: honours an explicit {@code kind} override
     * from {@code model-catalog.json} when it names a valid enum constant,
     * otherwise falls back to the {@link #classify(String, String) heuristic}.
     *
     * @param override the raw {@code kind} text from the catalog (may be {@code null})
     * @param id       the model id
     * @param label    the model label
     */
    public static TurLlmModelKind parseOrClassify(String override, String id, String label) {
        if (StringUtils.hasText(override)) {
            try {
                return valueOf(override.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Unknown override name — fall through to the heuristic.
            }
        }
        return classify(id, label);
    }

    /**
     * Authoritative mapping from Gemini's {@code supportedGenerationMethods}
     * (native GenAI {@code /models} lists it per model) — far more reliable than
     * the name heuristic. Falls back to {@link #classify} when the methods are
     * absent or unrecognised.
     *
     * @param methods the {@code supportedGenerationMethods} values (may be {@code null})
     * @param id      the model id (for the heuristic fallback)
     * @param label   the model label (for the heuristic fallback)
     */
    public static TurLlmModelKind fromGeminiGenerationMethods(Collection<?> methods, String id, String label) {
        if (methods != null) {
            boolean embed = false;
            boolean generate = false;
            boolean predict = false;
            boolean predictLong = false;
            for (Object o : methods) {
                if (o == null) {
                    continue;
                }
                String m = o.toString().toLowerCase(Locale.ROOT);
                embed |= m.contains(KW_EMBED);
                generate |= m.contains("generatecontent"); // covers bidiGenerateContent
                predictLong |= m.contains("predictlongrunning");
                predict |= m.equals("predict");
            }
            if (embed) {
                return EMBEDDING;
            }
            if (predictLong) {
                return VIDEO;
            }
            if (generate) {
                return CHAT;
            }
            if (predict) {
                return IMAGE;
            }
        }
        return classify(id, label);
    }

    /**
     * Authoritative mapping from a vendor's {@code endpoints} list (Cohere lists
     * {@code ["chat"]}/{@code ["embed"]}/{@code ["rerank"]}/{@code ["classify"]}
     * per model). Falls back to {@link #classify} when absent or unrecognised.
     *
     * @param endpoints the {@code endpoints} values (may be {@code null})
     * @param id        the model id (for the heuristic fallback)
     * @param label     the model label (for the heuristic fallback)
     */
    public static TurLlmModelKind fromVendorEndpoints(Collection<?> endpoints, String id, String label) {
        if (endpoints != null) {
            boolean rerank = false;
            boolean embed = false;
            boolean chat = false;
            for (Object o : endpoints) {
                if (o == null) {
                    continue;
                }
                String e = o.toString().toLowerCase(Locale.ROOT);
                rerank |= e.contains("rerank");
                embed |= e.contains(KW_EMBED);
                chat |= e.contains("chat") || e.contains("generate");
            }
            // rerank before embed: a rerank endpoint is the most specific signal.
            if (rerank) {
                return RERANK;
            }
            if (embed) {
                return EMBEDDING;
            }
            if (chat) {
                return CHAT;
            }
        }
        return classify(id, label);
    }
}
