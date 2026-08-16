package com.viglet.turing.genai.provider.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.properties.TurVigletAiProperty;

/**
 * T956 / §XIV.10.1 — the managed Viglet AI provider pins both facts an operator
 * would otherwise have to know: the endpoint and the model name.
 */
@ExtendWith(MockitoExtension.class)
class TurVigletLlmProviderTest {

    @Mock
    private TurProviderOptionsParser optionsParser;
    @Mock
    private com.viglet.turing.genai.vigletai.TurVigletInstallIdentityService installIdentityService;

    private TurVigletAiProperty property;
    private TurVigletLlmProvider provider;
    private TurLLMInstance instance;

    @BeforeEach
    void setUp() {
        property = new TurVigletAiProperty();
        provider = new TurVigletLlmProvider(optionsParser, property, installIdentityService);
        instance = new TurLLMInstance();
        instance.setId("managed-instance");
    }

    @Test
    void pluginTypeIsViglet() {
        assertEquals("viglet", provider.getPluginType());
    }

    @Test
    void defaultsAreTheManagedEndpointAndTheOpaqueAlias() {
        assertEquals("https://llm.viglet.cloud/v1", property.getBaseUrl());
        assertEquals("viglet-fast", property.getModelAlias());
    }

    @Test
    void baseUrlIsPinnedEvenWhenTheInstanceCarriesAnother() {
        // The generic openai-compatible parent would honour both of these.
        instance.setUrl("https://api.deepseek.com/v1");
        lenient().when(optionsParser.stringValue(any(), any())).thenReturn("https://evil.example/v1");

        assertEquals("https://llm.viglet.cloud/v1", provider.resolveBaseUrl(instance, Map.of()));
    }

    @Test
    void modelIsPinnedToTheAliasForChatAndEmbeddingAlike() {
        instance.setModelName("gpt-4o-mini");
        lenient().when(optionsParser.stringValue(any(), any())).thenReturn("claude-sonnet-4-20250514");

        assertEquals("viglet-fast", provider.resolveChatModelName(instance, Map.of()));
        assertEquals("viglet-fast", provider.resolveEmbeddingModelName(instance, Map.of()));
    }

    @Test
    void listModelsReturnsTheSingleAliasWithoutProbingTheEndpoint() {
        List<TurLlmModelOption> models = provider.listModels(instance, null);

        assertEquals(1, models.size());
        assertEquals("viglet-fast", models.get(0).id());
        assertEquals("viglet-fast", models.get(0).label());
        assertEquals(TurLlmModelKind.CHAT, models.get(0).kind());
    }

    @Test
    void aBlankConfiguredEndpointFailsFast() {
        property.setBaseUrl("  ");

        assertThrows(IllegalStateException.class, () -> provider.resolveBaseUrl(instance, Map.of()));
    }

    @Test
    void aBlankConfiguredAliasFailsFast() {
        property.setModelAlias("");

        assertThrows(IllegalStateException.class, () -> provider.resolveChatModelName(instance, Map.of()));
    }

    @Test
    void chatModelBuildsWithNoInstanceUrlOrModelName() {
        // Nothing configured on the instance — the managed provider still builds.
        when(optionsParser.parse(any())).thenReturn(Map.of());
        when(optionsParser.doubleValue(any(), any())).thenReturn(null);
        when(optionsParser.intValue(any(), any())).thenReturn(null);

        assertNotNull(provider.createChatModel(instance, "viglet-credential"));
    }

    @Test
    void embeddingModelBuildsWithNoInstanceUrlOrModelName() {
        when(optionsParser.parse(any())).thenReturn(Map.of());

        assertNotNull(provider.createEmbeddingModel(instance, "viglet-credential"));
    }
}
