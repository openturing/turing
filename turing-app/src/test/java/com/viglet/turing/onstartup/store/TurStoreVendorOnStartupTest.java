package com.viglet.turing.onstartup.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.store.TurStoreVendorRepository;

/**
 * T1043 — the seeding used to be guarded by {@code findAll().isEmpty()}, and a
 * test here called {@code shouldNotCreateRowsWhenRepositoryHasData} asserted that
 * guard: no row is ever written to a non-empty table. That is the defect, not the
 * contract — it makes the catalog a fresh-install-only write, so a vendor added in
 * a later release cannot be selected on any database that has ever started the
 * application. What replaces it is the per-id contract.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurStoreVendorOnStartupTest {

    @Mock
    private TurStoreVendorRepository turStoreVendorRepository;

    @InjectMocks
    private TurStoreVendorOnStartup turStoreVendorOnStartup;

    @Test
    @DisplayName("An empty catalog gets every vendor, in display order")
    void seedsEveryVendorIntoAnEmptyCatalog() {
        when(turStoreVendorRepository.existsById(anyString())).thenReturn(false);

        turStoreVendorOnStartup.createDefaultRows();

        ArgumentCaptor<TurStoreVendor> captor = ArgumentCaptor.forClass(TurStoreVendor.class);
        verify(turStoreVendorRepository, org.mockito.Mockito.times(4)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(TurStoreVendor::getId)
                .containsExactly("CHROMA", "MILVUS", "PGVECTOR", "LUCENE");
        assertThat(captor.getAllValues()).extracting(TurStoreVendor::getPlugin)
                .containsExactly("chroma", "milvus", "pgvector", "lucene");
    }

    @Test
    @DisplayName("An existing install still gets a vendor added in a later release")
    void addsOnlyTheMissingVendorToAPopulatedCatalog() {
        when(turStoreVendorRepository.existsById(anyString())).thenReturn(true);
        when(turStoreVendorRepository.existsById("PGVECTOR")).thenReturn(false);

        turStoreVendorOnStartup.createDefaultRows();

        ArgumentCaptor<TurStoreVendor> captor = ArgumentCaptor.forClass(TurStoreVendor.class);
        verify(turStoreVendorRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("PGVECTOR");
    }

    @Test
    @DisplayName("A full catalog is left alone — an operator's edits are never overwritten")
    void writesNothingWhenEveryVendorIsPresent() {
        when(turStoreVendorRepository.existsById(anyString())).thenReturn(true);

        turStoreVendorOnStartup.createDefaultRows();

        verify(turStoreVendorRepository, never())
                .save(org.mockito.ArgumentMatchers.any(TurStoreVendor.class));
        verify(turStoreVendorRepository, never()).findAll();
    }

    @Test
    @DisplayName("Every vendor id fits the column, which is VARCHAR(10)")
    void vendorIdsFitTheColumn() {
        when(turStoreVendorRepository.existsById(anyString())).thenReturn(false);

        turStoreVendorOnStartup.createDefaultRows();

        ArgumentCaptor<TurStoreVendor> captor = ArgumentCaptor.forClass(TurStoreVendor.class);
        verify(turStoreVendorRepository, org.mockito.Mockito.times(4)).save(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(vendor -> assertThat(vendor.getId()).hasSizeLessThan(11));
    }

    @Test
    @DisplayName("Each seeded vendor names a store plugin that exists")
    void everyVendorNamesARealPlugin() {
        when(turStoreVendorRepository.existsById(anyString())).thenReturn(false);

        turStoreVendorOnStartup.createDefaultRows();

        // The four plugin types TurGenAiStoreProviderFactory resolves. A vendor row
        // naming a fifth would offer an admin a store nothing implements.
        ArgumentCaptor<TurStoreVendor> captor = ArgumentCaptor.forClass(TurStoreVendor.class);
        verify(turStoreVendorRepository, org.mockito.Mockito.times(4)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(TurStoreVendor::getPlugin)
                .allSatisfy(plugin -> assertThat(plugin)
                        .isIn("chroma", "milvus", "pgvector", "lucene"));
    }
}
