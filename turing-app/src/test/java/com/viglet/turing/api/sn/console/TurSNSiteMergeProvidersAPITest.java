package com.viglet.turing.api.sn.console;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.dto.sn.merge.TurSNSiteMergeProvidersDto;
import com.viglet.turing.persistence.mapper.sn.merge.TurSNSiteMergeProvidersMapper;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProvidersField;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersFieldRepository;
import com.viglet.turing.persistence.repository.sn.merge.TurSNSiteMergeProvidersRepository;

@ExtendWith(MockitoExtension.class)
class TurSNSiteMergeProvidersAPITest {

    @Mock
    private TurSNSiteRepository turSNSiteRepository;
    @Mock
    private TurSNSiteMergeProvidersRepository turSNSiteMergeRepository;
    @Mock
    private TurSNSiteMergeProvidersFieldRepository turSNSiteMergeFieldRepository;

    private TurSNSiteMergeProvidersMapper turSNSiteMergeProvidersMapper;

    private TurSNSiteMergeProvidersAPI api;

    @BeforeEach
    void setUp() {
        turSNSiteMergeProvidersMapper = Mappers.getMapper(TurSNSiteMergeProvidersMapper.class);
        api = new TurSNSiteMergeProvidersAPI(turSNSiteRepository, turSNSiteMergeRepository,
                turSNSiteMergeFieldRepository, turSNSiteMergeProvidersMapper);
    }

    @Test
    void shouldListMergeProvidersWhenSiteExists() {
        TurSNSite site = new TurSNSite();
        TurSNSiteMergeProviders provider = new TurSNSiteMergeProviders();
        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));
        when(turSNSiteMergeRepository.findByTurSNSite(site)).thenReturn(List.of(provider));

        List<TurSNSiteMergeProvidersDto> result = api.turSNSiteMergeList("site-1");

        assertThat(result).hasSize(1);
    }

    @Test
    void shouldReturnEmptyListWhenSiteDoesNotExist() {
        when(turSNSiteRepository.findById("missing")).thenReturn(Optional.empty());

        List<TurSNSiteMergeProvidersDto> result = api.turSNSiteMergeList("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnMergeProviderWithOverwrittenFields() {
        TurSNSiteMergeProviders mergeProviders = new TurSNSiteMergeProviders();
        mergeProviders.setProviderFrom("provider-a");
        TurSNSiteMergeProvidersField overwrittenField = new TurSNSiteMergeProvidersField();
        when(turSNSiteMergeRepository.findById("merge-1")).thenReturn(Optional.of(mergeProviders));
        when(turSNSiteMergeFieldRepository.findByTurSNSiteMergeProviders(mergeProviders))
                .thenReturn(Set.of(overwrittenField));

        TurSNSiteMergeProvidersDto result = api.turSNSiteFieldExtGet("ignored", "merge-1");

        assertThat(result.getProviderFrom()).isEqualTo("provider-a");
        assertThat(result.getOverwrittenFields()).containsExactly(overwrittenField);
    }

    @Test
    void shouldReturnNewMergeProviderWhenIdNotFound() {
        when(turSNSiteMergeRepository.findById("missing")).thenReturn(Optional.empty());

        TurSNSiteMergeProvidersDto result = api.turSNSiteFieldExtGet("ignored", "missing");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isNull();
    }

    @Test
    void shouldUpdateMergeProviderAndPersistOverwrittenFields() {
        TurSNSite site = new TurSNSite();
        TurSNSiteMergeProviders existing = new TurSNSiteMergeProviders();
        existing.setOverwrittenFields(new java.util.HashSet<>(Set.of(new TurSNSiteMergeProvidersField())));

        TurSNSiteMergeProvidersField firstField = new TurSNSiteMergeProvidersField();
        TurSNSiteMergeProvidersField secondField = new TurSNSiteMergeProvidersField();

        TurSNSiteMergeProvidersDto request = new TurSNSiteMergeProvidersDto();
        request.setProviderFrom("provider-a");
        request.setProviderTo("provider-b");
        request.setRelationFrom("from");
        request.setRelationTo("to");
        request.setDescription("updated");
        request.setLocale(Locale.CANADA);
        request.setTurSNSite(site);
        request.setOverwrittenFields(Set.of(firstField, secondField));

        when(turSNSiteMergeRepository.findById("merge-1")).thenReturn(Optional.of(existing));

        TurSNSiteMergeProvidersDto result = api.turSNSiteMergeUpdate("merge-1", request, "ignored");

        assertThat(result.getProviderFrom()).isEqualTo("provider-a");
        assertThat(result.getProviderTo()).isEqualTo("provider-b");
        assertThat(result.getRelationFrom()).isEqualTo("from");
        assertThat(result.getRelationTo()).isEqualTo("to");
        assertThat(result.getDescription()).isEqualTo("updated");
        assertThat(result.getLocale()).isEqualTo(Locale.CANADA);
        assertThat(result.getTurSNSite()).isNotNull();

        verify(turSNSiteMergeRepository).save(existing);
        verify(turSNSiteMergeFieldRepository).save(firstField);
        verify(turSNSiteMergeFieldRepository).save(secondField);
        assertThat(firstField.getTurSNSiteMergeProviders()).isSameAs(existing);
        assertThat(secondField.getTurSNSiteMergeProviders()).isSameAs(existing);
    }

    @Test
    void shouldReturnNewMergeProviderWhenUpdateIdNotFound() {
        when(turSNSiteMergeRepository.findById("missing")).thenReturn(Optional.empty());

        TurSNSiteMergeProvidersDto result = api.turSNSiteMergeUpdate("missing", new TurSNSiteMergeProvidersDto(),
                "ignored");

        assertThat(result.getId()).isNull();
    }

    @Test
    void shouldDeleteMergeProviderById() {
        boolean deleted = api.turSNSiteMergeDelete("merge-1", "ignored");

        assertThat(deleted).isTrue();
        verify(turSNSiteMergeRepository).deleteById("merge-1");
    }

    @Test
    void shouldAddMergeProviderAndPersistFields() {
        TurSNSiteMergeProvidersDto merge = new TurSNSiteMergeProvidersDto();
        merge.setProviderFrom("from-provider");
        TurSNSiteMergeProvidersField field = new TurSNSiteMergeProvidersField();
        merge.setOverwrittenFields(Set.of(field));

        TurSNSiteMergeProvidersDto result = api.turSNSiteMergeAdd(merge, "ignored");

        assertThat(result.getProviderFrom()).isEqualTo("from-provider");
        verify(turSNSiteMergeRepository).save(any(TurSNSiteMergeProviders.class));
        verify(turSNSiteMergeFieldRepository).save(field);
        assertThat(field.getTurSNSiteMergeProviders()).isNotNull();
    }

    @Test
    void shouldCreateMergeStructureWithDefaultLocaleWhenSiteExists() {
        TurSNSite site = new TurSNSite();
        when(turSNSiteRepository.findById("site-1")).thenReturn(Optional.of(site));

        TurSNSiteMergeProvidersDto structure = api.turSNSiteMergeStructure("site-1");

        assertThat(structure.getLocale()).isEqualTo(Locale.US);
        assertThat(structure.getTurSNSite()).isSameAs(site);
    }

    @Test
    void shouldReturnEmptyMergeStructureWhenSiteNotFound() {
        when(turSNSiteRepository.findById("missing")).thenReturn(Optional.empty());

        TurSNSiteMergeProvidersDto structure = api.turSNSiteMergeStructure("missing");

        assertThat(structure.getId()).isNull();
        assertThat(structure.getTurSNSite()).isNull();
        assertThat(structure.getOverwrittenFields()).isNotNull();
    }
}
