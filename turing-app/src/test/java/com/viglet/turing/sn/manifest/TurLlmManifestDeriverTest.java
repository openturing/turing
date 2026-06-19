package com.viglet.turing.sn.manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.core.manifest.VigletFieldManifest;
import com.viglet.core.manifest.VigletFieldSpec;
import com.viglet.core.manifest.VigletFieldType;
import com.viglet.core.manifest.VigletManifestDeriveRequest;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.security.TurSecretCryptoService;

@ExtendWith(MockitoExtension.class)
class TurLlmManifestDeriverTest {

    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @Mock
    private TurLLMInstanceRepository llmInstanceRepository;
    @Mock
    private TurLlmModelFactory llmModelFactory;
    @Mock
    private TurSecretCryptoService secretCryptoService;

    private TurLlmManifestDeriver deriver() {
        return new TurLlmManifestDeriver(globalSettingsService,
                llmInstanceRepository, llmModelFactory, secretCryptoService);
    }

    private static Map<String, Object> doc(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private VigletFieldSpec field(List<VigletFieldSpec> fields, String name) {
        return fields.stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void derivesHeuristicDraftWhenNoLlmConfigured() {
        when(globalSettingsService.getDefaultLlmId()).thenReturn(null);
        TurLlmManifestDeriver deriver = deriver();

        assertFalse(deriver.isLlmAvailable());

        VigletFieldManifest draft = deriver.derive(new VigletManifestDeriveRequest(
                "courses", "Course catalog", "se1", List.of("pt_BR"),
                List.of(
                        doc("name", "Algorithms", "modalidade", "online", "price", 1500.0, "credits", 4),
                        doc("name", "Calculus", "modalidade", "presencial", "price", 2000.0, "credits", 6))));

        // Site identity is echoed straight from the request for round-trip review.
        assertEquals("courses", draft.name());
        assertEquals("se1", draft.seInstanceId());
        assertEquals(List.of("pt_BR"), draft.locales());
        assertEquals("1", draft.schemaVersion());

        List<VigletFieldSpec> f = draft.fields();
        assertEquals(4, f.size());
        assertEquals(VigletFieldType.STRING, field(f, "name").type());
        assertTrue(field(f, "modalidade").facet());            // low-cardinality string
        assertEquals(VigletFieldType.DOUBLE, field(f, "price").type());
        assertTrue(field(f, "price").facet());                 // numeric -> range facet
        assertTrue(field(f, "name").mandatory());              // present in every doc
    }

    @Test
    void rejectsEmptySample() {
        lenient().when(globalSettingsService.getDefaultLlmId()).thenReturn(null);
        TurLlmManifestDeriver deriver = deriver();

        var emptyDocsRequest = new VigletManifestDeriveRequest("x", null, "se1", List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> deriver.derive(emptyDocsRequest));
        var nullDocsRequest = new VigletManifestDeriveRequest("x", null, "se1", List.of(), null);
        assertThrows(IllegalArgumentException.class, () -> deriver.derive(nullDocsRequest));
    }

    @Test
    void rejectsSampleWithNoFields() {
        lenient().when(globalSettingsService.getDefaultLlmId()).thenReturn(null);
        TurLlmManifestDeriver deriver = deriver();

        var noFieldsRequest = new VigletManifestDeriveRequest("x", null, "se1", List.of(), List.of(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> deriver.derive(noFieldsRequest));
    }

    @Test
    void extractsJsonArrayFromFencedReply() {
        assertEquals("[{\"name\":\"a\"}]",
                TurLlmManifestDeriver.extractJsonArray("```json\n[{\"name\":\"a\"}]\n```"));
        assertEquals("[1,2]",
                TurLlmManifestDeriver.extractJsonArray("Here you go: [1,2] — enjoy"));
    }
}
