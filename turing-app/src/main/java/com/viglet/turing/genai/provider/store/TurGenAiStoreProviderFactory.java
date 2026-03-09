package com.viglet.turing.genai.provider.store;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.store.TurStoreInstance;

@Component
public class TurGenAiStoreProviderFactory {

    private final Map<String, TurGenAiStoreProvider> providerMap;

    public TurGenAiStoreProviderFactory(List<TurGenAiStoreProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(provider -> provider.getPluginType().toLowerCase(Locale.ROOT),
                        Function.identity()));
    }

    public TurGenAiStoreProvider getProvider(TurStoreInstance turStoreInstance) {
        String pluginType = resolvePluginType(turStoreInstance);
        TurGenAiStoreProvider provider = providerMap.get(pluginType);
        if (provider == null) {
            throw new IllegalStateException(
                    "Unsupported embedding store provider '" + pluginType + "'. Available: " + providerMap.keySet());
        }
        return provider;
    }

    private String resolvePluginType(TurStoreInstance turStoreInstance) {
        if (turStoreInstance == null || turStoreInstance.getTurStoreVendor() == null) {
            throw new IllegalStateException("Embedding store vendor configuration is missing");
        }
        String plugin = turStoreInstance.getTurStoreVendor().getPlugin();
        if (!StringUtils.hasText(plugin)) {
            plugin = turStoreInstance.getTurStoreVendor().getId();
        }
        return plugin.toLowerCase(Locale.ROOT);
    }
}
