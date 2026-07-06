package com.viglet.turing.genai.provider.llm;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMVendorRepository;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the selectable models for the LLM-instance model picker (T577).
 *
 * <p>Given a vendor (and, optionally, a typed-but-unsaved API key / URL, or an
 * existing instance id to reuse its stored key), it asks the matching
 * {@link TurGenAiLlmProvider} for a <b>live</b> list from the vendor API. When
 * that returns nothing — no key yet, offline, or a provider without a list
 * endpoint — it falls back to the bundled {@link TurLlmModelCatalog static
 * catalog}. The result records which source was used so the UI can badge it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurLlmModelDiscoveryService {

    /** Which source produced the model list, for UI badging. */
    public enum Source { LIVE, CATALOG, NONE }

    /** The picker payload: where the list came from + the models themselves. */
    public record Result(Source source, List<TurLlmModelOption> models) {
        static Result of(Source source, List<TurLlmModelOption> models) {
            return new Result(source, models);
        }
    }

    private final TurGenAiLlmProviderFactory providerFactory;
    private final TurLlmModelCatalog catalog;
    private final TurLLMVendorRepository vendorRepository;
    private final TurLLMInstanceRepository instanceRepository;
    private final TurSecretCryptoService secretCryptoService;

    public TurLlmModelDiscoveryService(TurGenAiLlmProviderFactory providerFactory,
            TurLlmModelCatalog catalog,
            TurLLMVendorRepository vendorRepository,
            TurLLMInstanceRepository instanceRepository,
            TurSecretCryptoService secretCryptoService) {
        this.providerFactory = providerFactory;
        this.catalog = catalog;
        this.vendorRepository = vendorRepository;
        this.instanceRepository = instanceRepository;
        this.secretCryptoService = secretCryptoService;
    }

    /**
     * @param vendorId            required — the {@code TurLLMVendor} id (e.g. "OPENAI")
     * @param instanceId          optional — reuse this instance's stored API key when no
     *                            {@code apiKey} is supplied
     * @param apiKey              optional — a typed-but-unsaved plaintext key (takes precedence)
     * @param url                 optional — the endpoint URL from the form (for base-url resolution)
     * @param providerOptionsJson optional — provider options from the form (base-url overrides, etc.)
     */
    public Result listModels(String vendorId, String instanceId, String apiKey, String url,
            String providerOptionsJson) {
        TurLLMVendor vendor = StringUtils.hasText(vendorId)
                ? vendorRepository.findById(vendorId).orElse(null)
                : null;
        if (vendor == null) {
            return Result.of(Source.NONE, List.of());
        }

        TurLLMInstance probe = new TurLLMInstance();
        probe.setTurLLMVendor(vendor);
        probe.setUrl(url);
        probe.setProviderOptionsJson(providerOptionsJson);

        String decryptedKey = resolveKey(apiKey, instanceId);

        List<TurLlmModelOption> live = List.of();
        try {
            live = providerFactory.getProvider(probe).listModels(probe, decryptedKey);
        } catch (Exception e) {
            // Unknown/misconfigured provider — degrade to the static catalog.
            log.debug("Live model listing unavailable for vendor '{}': {}", vendorId, e.getMessage());
        }
        if (live != null && !live.isEmpty()) {
            return Result.of(Source.LIVE, live);
        }

        List<TurLlmModelOption> staticModels = catalog.staticModels(resolvePluginType(vendor));
        return Result.of(staticModels.isEmpty() ? Source.NONE : Source.CATALOG, staticModels);
    }

    private String resolveKey(String typedApiKey, String instanceId) {
        if (StringUtils.hasText(typedApiKey)) {
            return typedApiKey;
        }
        if (StringUtils.hasText(instanceId)) {
            return instanceRepository.findById(instanceId)
                    .map(TurLLMInstance::getApiKeyEncrypted)
                    .filter(StringUtils::hasText)
                    .map(secretCryptoService::decrypt)
                    .orElse(null);
        }
        return null;
    }

    /** Mirrors {@link TurGenAiLlmProviderFactory}: plugin, else id, lower-cased. */
    private String resolvePluginType(TurLLMVendor vendor) {
        String plugin = StringUtils.hasText(vendor.getPlugin()) ? vendor.getPlugin() : vendor.getId();
        return plugin == null ? "" : plugin.toLowerCase(Locale.ROOT);
    }
}
