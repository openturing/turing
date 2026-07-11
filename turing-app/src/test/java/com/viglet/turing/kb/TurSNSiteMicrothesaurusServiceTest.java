package com.viglet.turing.kb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.dto.kb.TurSNSiteMicrothesaurusConfigDto;
import com.viglet.turing.persistence.dto.kb.TurSNSiteMicrothesaurusDto;
import com.viglet.turing.persistence.model.kb.TurMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurSNSiteMicrothesaurus;
import com.viglet.turing.persistence.model.kb.TurSNSiteMicrothesaurusConfig;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.kb.TurMicrothesaurusRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusConfigRepository;
import com.viglet.turing.persistence.repository.kb.TurSNSiteMicrothesaurusRepository;
import com.viglet.turing.sn.field.TurSNFieldProvisioner;
import com.viglet.turing.sn.kb.TurMicrothesaurusDictionaryService;

/**
 * T670 / §XL (Block AQ) — unit tests for per-site microthesaurus selection +
 * index config: selection validates the microthesaurus exists and is unique,
 * config defaults are returned when none is stored, and saving an <em>enabled</em>
 * config provisions the backing TEXT field (the TurSNFieldProvisioner wiring).
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteMicrothesaurusServiceTest {

    @Mock
    private TurSNSiteMicrothesaurusRepository selectionRepository;
    @Mock
    private TurSNSiteMicrothesaurusConfigRepository configRepository;
    @Mock
    private TurMicrothesaurusRepository microthesaurusRepository;
    @Mock
    private TurSNFieldProvisioner fieldProvisioner;
    @Mock
    private TurMicrothesaurusDictionaryService dictionaryService;

    @InjectMocks
    private TurSNSiteMicrothesaurusService service;

    private final TurSNSite site = new TurSNSite();

    private TurMicrothesaurus tree(String id) {
        TurMicrothesaurus m = new TurMicrothesaurus();
        m.setId(id);
        m.setName("Educação");
        m.setLanguage(Locale.forLanguageTag("pt"));
        m.setDomain("EDUCATION");
        return m;
    }

    @Test
    void selectRejectsBlankMicrothesaurusId() {
        assertThatThrownBy(() -> service.select(site,
                new TurSNSiteMicrothesaurusDto(null, "  ", true, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microthesaurusId is required");
        verify(selectionRepository, never()).save(any());
    }

    @Test
    void selectRejectsUnknownMicrothesaurus() {
        when(microthesaurusRepository.findById("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.select(site,
                new TurSNSiteMicrothesaurusDto(null, "x", true, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown microthesaurus");
    }

    @Test
    void selectRejectsDuplicate() {
        when(microthesaurusRepository.findById("m1")).thenReturn(Optional.of(tree("m1")));
        when(selectionRepository.existsByTurSNSiteAndMicrothesaurusId(site, "m1")).thenReturn(true);
        assertThatThrownBy(() -> service.select(site,
                new TurSNSiteMicrothesaurusDto(null, "m1", true, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already selected");
    }

    @Test
    void selectPersistsAndResolvesDisplayFields() {
        when(microthesaurusRepository.findById("m1")).thenReturn(Optional.of(tree("m1")));
        when(selectionRepository.existsByTurSNSiteAndMicrothesaurusId(site, "m1")).thenReturn(false);
        when(selectionRepository.save(any(TurSNSiteMicrothesaurus.class)))
                .thenAnswer(i -> i.getArgument(0));

        TurSNSiteMicrothesaurusDto result = service.select(site,
                new TurSNSiteMicrothesaurusDto(null, "m1", true, null, null, null));

        assertThat(result.microthesaurusId()).isEqualTo("m1");
        assertThat(result.name()).isEqualTo("Educação");
        assertThat(result.domain()).isEqualTo("EDUCATION");
    }

    @Test
    void getConfigReturnsDefaultsWhenNoneStored() {
        when(configRepository.findByTurSNSite(site)).thenReturn(Optional.empty());
        TurSNSiteMicrothesaurusConfigDto cfg = service.getConfig(site);
        assertThat(cfg.enabled()).isFalse();
        assertThat(cfg.fieldName()).isEqualTo(TurSNSiteMicrothesaurusConfig.DEFAULT_FIELD_NAME);
        assertThat(cfg.boost()).isEqualTo(TurSNSiteMicrothesaurusConfig.DEFAULT_BOOST);
        assertThat(cfg.includeSynonyms()).isTrue();
        assertThat(cfg.pathFacetEnabled()).isFalse();
        assertThat(cfg.pathFieldName())
                .isEqualTo(TurSNSiteMicrothesaurusConfig.DEFAULT_PATH_FIELD_NAME);
    }

    @Test
    void saveEnabledConfigProvisionsBackingField() {
        when(configRepository.findByTurSNSite(site)).thenReturn(Optional.empty());
        when(configRepository.save(any(TurSNSiteMicrothesaurusConfig.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.saveConfig(site,
                new TurSNSiteMicrothesaurusConfigDto(true, "  ", 0, true, false, "  "));

        ArgumentCaptor<TurSNJobAttributeSpec> spec = ArgumentCaptor.forClass(TurSNJobAttributeSpec.class);
        verify(fieldProvisioner).ensureField(any(TurSNSite.class), spec.capture());
        assertThat(spec.getValue().getName())
                .isEqualTo(TurSNSiteMicrothesaurusConfig.DEFAULT_FIELD_NAME);
        assertThat(spec.getValue().getType()).isEqualTo(TurSEFieldType.TEXT);
        assertThat(spec.getValue().isMultiValued()).isTrue();
        assertThat(spec.getValue().isFacet()).isTrue();
    }

    @Test
    void saveWithPathFacetProvisionsBothFields() {
        when(configRepository.findByTurSNSite(site)).thenReturn(Optional.empty());
        when(configRepository.save(any(TurSNSiteMicrothesaurusConfig.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.saveConfig(site,
                new TurSNSiteMicrothesaurusConfigDto(true, "  ", 0, true, true, "  "));

        ArgumentCaptor<TurSNJobAttributeSpec> spec = ArgumentCaptor.forClass(TurSNJobAttributeSpec.class);
        verify(fieldProvisioner, times(2)).ensureField(any(TurSNSite.class), spec.capture());
        assertThat(spec.getAllValues()).extracting(TurSNJobAttributeSpec::getName)
                .containsExactly(TurSNSiteMicrothesaurusConfig.DEFAULT_FIELD_NAME,
                        TurSNSiteMicrothesaurusConfig.DEFAULT_PATH_FIELD_NAME);
        assertThat(spec.getAllValues()).extracting(TurSNJobAttributeSpec::getType)
                .containsExactly(TurSEFieldType.TEXT, TurSEFieldType.STRING);
    }

    @Test
    void saveDisabledConfigDoesNotProvision() {
        when(configRepository.findByTurSNSite(site)).thenReturn(Optional.empty());
        when(configRepository.save(any(TurSNSiteMicrothesaurusConfig.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.saveConfig(site,
                new TurSNSiteMicrothesaurusConfigDto(false, "microthesaurus_terms", 0.8, true,
                        false, "microthesaurus_path"));

        verify(fieldProvisioner, never()).ensureField(any(), any());
    }
}
