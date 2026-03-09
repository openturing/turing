package com.viglet.turing.onstartup.se;

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

import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.repository.se.TurSEVendorRepository;

@ExtendWith(MockitoExtension.class)
class TurSEVendorOnStartupTest {

    @Mock
    private TurSEVendorRepository turSEVendorRepository;

    @InjectMocks
    private TurSEVendorOnStartup turSEVendorOnStartup;

    @Test
    void shouldCreateDefaultRowsWhenRepositoryIsEmpty() {
        when(turSEVendorRepository.findAll()).thenReturn(List.of());

        turSEVendorOnStartup.createDefaultRows();

        ArgumentCaptor<TurSEVendor> captor = ArgumentCaptor.forClass(TurSEVendor.class);
        verify(turSEVendorRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<TurSEVendor> vendors = captor.getAllValues();
        assertEquals("SOLR", vendors.get(0).getId());
        assertEquals("LUCENE", vendors.get(1).getId());
    }

    @Test
    void shouldNotCreateRowsWhenRepositoryHasData() {
        when(turSEVendorRepository.findAll()).thenReturn(List.of(new TurSEVendor()));

        turSEVendorOnStartup.createDefaultRows();

        verify(turSEVendorRepository, never()).save(org.mockito.ArgumentMatchers.any(TurSEVendor.class));
    }
}
