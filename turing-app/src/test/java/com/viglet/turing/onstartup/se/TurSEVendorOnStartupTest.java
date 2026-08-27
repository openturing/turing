package com.viglet.turing.onstartup.se;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;

/**
 * The seeding used to be guarded by {@code findAll().isEmpty()}, and this class
 * asserted that guard: a second test called
 * {@code shouldNotCreateRowsWhenRepositoryHasData} pinned the behaviour that no
 * row is ever written to a non-empty table. That is precisely the defect T1031
 * had to fix — it made the vendor catalog a fresh-install-only write, so
 * {@code CONTENTAI}, the first vendor added after the table existed, could not be
 * selected on any database that had ever started the application. What replaces
 * it is the per-id contract: absent rows are inserted, present rows are left
 * exactly as the operator has them.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurSEVendorOnStartupTest {

    @Mock
    private TurSEVendorRepository turSEVendorRepository;

    @InjectMocks
    private TurSEVendorOnStartup turSEVendorOnStartup;

    @Test
    @DisplayName("An empty catalog gets every vendor, Content AI included")
    void seedsEveryVendorIntoAnEmptyCatalog() {
        when(turSEVendorRepository.existsById(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(false);

        turSEVendorOnStartup.createDefaultRows();

        ArgumentCaptor<TurSEVendor> captor = ArgumentCaptor.forClass(TurSEVendor.class);
        verify(turSEVendorRepository, org.mockito.Mockito.times(4)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(TurSEVendor::getId)
                .containsExactly("SOLR", "LUCENE", "ES", "CONTENTAI");
        assertThat(captor.getAllValues()).extracting(TurSEVendor::getPlugin)
                .containsExactly("solr", "lucene", "elasticsearch", "contentai");
    }

    @Test
    @DisplayName("An existing install still gets a vendor added in a later release")
    void addsOnlyTheMissingVendorToAPopulatedCatalog() {
        when(turSEVendorRepository.existsById("SOLR")).thenReturn(true);
        when(turSEVendorRepository.existsById("LUCENE")).thenReturn(true);
        when(turSEVendorRepository.existsById("ES")).thenReturn(true);
        when(turSEVendorRepository.existsById("CONTENTAI")).thenReturn(false);

        turSEVendorOnStartup.createDefaultRows();

        ArgumentCaptor<TurSEVendor> captor = ArgumentCaptor.forClass(TurSEVendor.class);
        verify(turSEVendorRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("CONTENTAI");
        assertThat(captor.getValue().getPlugin()).isEqualTo("contentai");
    }

    @Test
    @DisplayName("A full catalog is left alone — an operator's edits are never overwritten")
    void writesNothingWhenEveryVendorIsPresent() {
        when(turSEVendorRepository.existsById(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);

        turSEVendorOnStartup.createDefaultRows();

        verify(turSEVendorRepository, never())
                .save(org.mockito.ArgumentMatchers.any(TurSEVendor.class));
        verify(turSEVendorRepository, never()).findAll();
    }

    @Test
    @DisplayName("The vendor id fits the column, which is VARCHAR(10)")
    void vendorIdsFitTheColumn() {
        when(turSEVendorRepository.existsById(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(false);

        turSEVendorOnStartup.createDefaultRows();

        ArgumentCaptor<TurSEVendor> captor = ArgumentCaptor.forClass(TurSEVendor.class);
        verify(turSEVendorRepository, org.mockito.Mockito.times(4)).save(captor.capture());
        List<TurSEVendor> vendors = captor.getAllValues();
        assertThat(vendors).allSatisfy(vendor -> assertThat(vendor.getId()).hasSizeLessThan(11));
    }
}
