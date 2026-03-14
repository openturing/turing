package com.viglet.turing.email.provider;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

@Component
public class TurEmailProviderFactory {
    private final Map<String, TurEmailProvider> providerMap;

    public TurEmailProviderFactory(List<TurEmailProvider> providers) {
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(
                        p -> p.getProviderType().toLowerCase(Locale.ROOT),
                        Function.identity()));
    }

    public TurEmailProvider getProvider(String providerType) {
        if (providerType == null || providerType.isBlank()) {
            throw new IllegalArgumentException("Email provider type must not be empty");
        }
        TurEmailProvider provider = providerMap.get(providerType.toLowerCase(Locale.ROOT));
        if (provider == null) {
            throw new IllegalStateException("No email provider found for type: " + providerType);
        }
        return provider;
    }
}
