package com.viglet.turing.genai.provider.llm;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;

@Component
public class TurGenAiLlmProviderFactory {

    private final Map<String, TurGenAiLlmProvider> providerMap;

    public TurGenAiLlmProviderFactory(List<TurGenAiLlmProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(provider -> provider.getPluginType().toLowerCase(Locale.ROOT),
                        Function.identity()));
    }

    public TurGenAiLlmProvider getProvider(TurLLMInstance turLLMInstance) {
        String pluginType = resolvePluginType(turLLMInstance);
        TurGenAiLlmProvider provider = providerMap.get(pluginType);
        if (provider == null) {
            throw new IllegalStateException(
                    "Unsupported LLM provider '" + pluginType + "'. Available: " + providerMap.keySet());
        }
        return provider;
    }

    private String resolvePluginType(TurLLMInstance turLLMInstance) {
        if (turLLMInstance == null || turLLMInstance.getTurLLMVendor() == null) {
            throw new IllegalStateException("LLM vendor configuration is missing");
        }
        String plugin = turLLMInstance.getTurLLMVendor().getPlugin();
        if (!StringUtils.hasText(plugin)) {
            plugin = turLLMInstance.getTurLLMVendor().getId();
        }
        return plugin.toLowerCase(Locale.ROOT);
    }
}
