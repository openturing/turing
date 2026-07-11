package com.viglet.turing.sn.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurSNSiteMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurSNSiteMicrothesaurusConfig;
import com.viglet.turing.persistence.model.kb.TurThesaurusTerm;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.kb.TurMicrothesaurusRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusConfigRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusRepository;
import com.viglet.turing.persistence.repository.kb.TurThesaurusTermRepository;
import com.viglet.turing.sn.field.TurSNFieldProvisioner;

/**
 * T672 / §XL (Block AQ) — index-time expansion through the real recognizer:
 * a document mentioning a controlled-vocabulary term is enriched with the term's
 * whole root→term hierarchical path in the configured field, the backing field
 * is provisioned, and an opted-out site is left byte-identical.
 */
@ExtendWith(MockitoExtension.class)
class TurSNMicrothesaurusIndexerTest {

    @Mock
    private TurSNSiteMicrothesaurusConfigRepository configRepository;
    @Mock
    private TurSNSiteMicrothesaurusRepository selectionRepository;
    @Mock
    private TurMicrothesaurusRepository microthesaurusRepository;
    @Mock
    private TurThesaurusTermRepository termRepository;
    @Mock
    private TurSNFieldProvisioner fieldProvisioner;

    private TurMicrothesaurusDictionaryService dictionaryService;
    private TurSNMicrothesaurusIndexer indexer;

    private final TurSNSite site = new TurSNSite();

    @BeforeEach
    void setUp() {
        site.setId("site1");
        dictionaryService = new TurMicrothesaurusDictionaryService(
                selectionRepository, configRepository, microthesaurusRepository, termRepository);
        indexer = new TurSNMicrothesaurusIndexer(configRepository, dictionaryService, fieldProvisioner);
    }

    private TurThesaurusTerm term(String id, String label, String parentId) {
        TurThesaurusTerm t = new TurThesaurusTerm();
        t.setId(id);
        t.setLabel(label);
        t.setParentTermId(parentId);
        t.setEnabled(true);
        return t;
    }

    private TurSNSiteMicrothesaurusConfig config(boolean enabled) {
        TurSNSiteMicrothesaurusConfig config = new TurSNSiteMicrothesaurusConfig();
        config.setEnabled(enabled);
        config.setFieldName(TurSNSiteMicrothesaurusConfig.DEFAULT_FIELD_NAME);
        config.setIncludeSynonyms(true);
        return config;
    }

    private void wireTree() {
        TurMicrothesaurus m = new TurMicrothesaurus();
        m.setId("m1");
        m.setLanguage(Locale.forLanguageTag("pt"));
        TurSNSiteMicrothesaurus selection = new TurSNSiteMicrothesaurus();
        selection.setMicrothesaurusId("m1");
        selection.setEnabled(true);
        lenient().when(selectionRepository.findByTurSNSiteAndEnabledTrue(site))
                .thenReturn(List.of(selection));
        lenient().when(microthesaurusRepository.findById("m1")).thenReturn(Optional.of(m));
        lenient().when(termRepository.findByTurMicrothesaurus(m)).thenReturn(List.of(
                term("d", "Doença", null),
                term("r", "Doença respiratória", "d"),
                term("p", "Pneumonia", "r")));
    }

    @Test
    void expandsDocumentWithHierarchicalPath() {
        when(configRepository.findByTurSNSite(site)).thenReturn(Optional.of(config(true)));
        wireTree();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.TITLE, "Diagnóstico de Pneumonia");
        attributes.put(TurSNFieldName.TEXT, "O paciente apresentou quadro respiratório.");

        indexer.enrich(site, Locale.forLanguageTag("pt"), attributes);

        Object expanded = attributes.get(TurSNSiteMicrothesaurusConfig.DEFAULT_FIELD_NAME);
        assertThat(expanded).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) expanded;
        assertThat(list).containsExactly("Doença", "Doença respiratória", "Pneumonia");
        verify(fieldProvisioner).ensureField(any(), any());
    }

    @Test
    void writesHierarchicalPathTokensWhenPathFacetEnabled() {
        TurSNSiteMicrothesaurusConfig cfg = config(true);
        cfg.setPathFacetEnabled(true);
        when(configRepository.findByTurSNSite(site)).thenReturn(Optional.of(cfg));
        wireTree();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.TITLE, "Diagnóstico de Pneumonia");

        indexer.enrich(site, Locale.forLanguageTag("pt"), attributes);

        Object path = attributes.get(TurSNSiteMicrothesaurusConfig.DEFAULT_PATH_FIELD_NAME);
        assertThat(path).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> tokens = (List<String>) path;
        assertThat(tokens).containsExactly(
                "0/Doença",
                "1/Doença/Doença respiratória",
                "2/Doença/Doença respiratória/Pneumonia");
    }

    @Test
    void doesNotWritePathTokensWhenPathFacetDisabled() {
        when(configRepository.findByTurSNSite(site)).thenReturn(Optional.of(config(true)));
        wireTree();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.TITLE, "Diagnóstico de Pneumonia");
        indexer.enrich(site, Locale.forLanguageTag("pt"), attributes);

        assertThat(attributes).containsKey(TurSNSiteMicrothesaurusConfig.DEFAULT_FIELD_NAME)
                .doesNotContainKey(TurSNSiteMicrothesaurusConfig.DEFAULT_PATH_FIELD_NAME);
    }

    @Test
    void pathTokensEscapeSlashInLabels() {
        assertThat(TurSNMicrothesaurusIndexer.pathTokens(List.of("A/B", "C")))
                .containsExactly("0/A\\/B", "1/A\\/B/C");
    }

    @Test
    void optedOutSiteIsUntouched() {
        when(configRepository.findByTurSNSite(site)).thenReturn(Optional.of(config(false)));

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.TITLE, "Pneumonia");
        indexer.enrich(site, Locale.forLanguageTag("pt"), attributes);

        assertThat(attributes).doesNotContainKey(TurSNSiteMicrothesaurusConfig.DEFAULT_FIELD_NAME);
        verify(fieldProvisioner, never()).ensureField(any(), any());
    }

    @Test
    void noConfigIsUntouched() {
        when(configRepository.findByTurSNSite(site)).thenReturn(Optional.empty());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.TITLE, "Pneumonia");
        indexer.enrich(site, Locale.forLanguageTag("pt"), attributes);

        assertThat(attributes).doesNotContainKey(TurSNSiteMicrothesaurusConfig.DEFAULT_FIELD_NAME);
    }

    @Test
    void documentWithNoRecognisedTermIsUntouched() {
        when(configRepository.findByTurSNSite(site)).thenReturn(Optional.of(config(true)));
        wireTree();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.TITLE, "Cronograma de matrículas 2026");
        indexer.enrich(site, Locale.forLanguageTag("pt"), attributes);

        assertThat(attributes).doesNotContainKey(TurSNSiteMicrothesaurusConfig.DEFAULT_FIELD_NAME);
    }
}
