package com.viglet.turing.onstartup.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.store.TurStoreVendor;
import com.viglet.turing.persistence.repository.store.TurStoreVendorRepository;

@ExtendWith(MockitoExtension.class)
class TurStoreVendorOnStartupTest {

    @Mock
    private TurStoreVendorRepository turStoreVendorRepository;

    @InjectMocks
    private TurStoreVendorOnStartup turStoreVendorOnStartup;

    @Test
    void shouldCreateDefaultRowsWhenRepositoryIsEmpty() {
        when(turStoreVendorRepository.findAll()).thenReturn(List.of());

        turStoreVendorOnStartup.createDefaultRows();

        ArgumentCaptor<TurStoreVendor> captor = ArgumentCaptor.forClass(TurStoreVendor.class);
        verify(turStoreVendorRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<TurStoreVendor> vendors = captor.getAllValues();
        assertEquals("CHROMA", vendors.get(0).getId());
        assertEquals("MILVUS", vendors.get(1).getId());
    }

    @Test
    void shouldNotCreateRowsWhenRepositoryHasData() {
        when(turStoreVendorRepository.findAll()).thenReturn(List.of(new TurStoreVendor()));

        turStoreVendorOnStartup.createDefaultRows();

        verify(turStoreVendorRepository, never()).save(org.mockito.ArgumentMatchers.any(TurStoreVendor.class));
    }
}
