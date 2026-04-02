package com.viglet.turing.service.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurStorageProperty;

/**
 * Tests for TurStorageConfig type resolution.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@ExtendWith(MockitoExtension.class)
class TurStorageConfigTest {

    @Mock
    private TurConfigProperties configProperties;

    @Test
    void shouldResolveNoneWhenNoConfigSet() {
        assertThat(TurStorageConfig.resolveType(configProperties)).isEqualTo(TurStorageType.NONE);
    }

    @Test
    void shouldResolveExplicitFilesystem() {
        TurStorageProperty storage = new TurStorageProperty();
        storage.setType(TurStorageType.FILESYSTEM);
        when(configProperties.getStorage()).thenReturn(storage);

        assertThat(TurStorageConfig.resolveType(configProperties)).isEqualTo(TurStorageType.FILESYSTEM);
    }

    @Test
    void shouldResolveExplicitMinio() {
        TurStorageProperty storage = new TurStorageProperty();
        storage.setType(TurStorageType.MINIO);
        when(configProperties.getStorage()).thenReturn(storage);

        assertThat(TurStorageConfig.resolveType(configProperties)).isEqualTo(TurStorageType.MINIO);
    }

    @Test
    void shouldResolveExplicitNone() {
        TurStorageProperty storage = new TurStorageProperty();
        storage.setType(TurStorageType.NONE);
        when(configProperties.getStorage()).thenReturn(storage);

        assertThat(TurStorageConfig.resolveType(configProperties)).isEqualTo(TurStorageType.NONE);
    }

    @Test
    void shouldReturnNoneWhenStorageHasNoType() {
        TurStorageProperty storage = new TurStorageProperty();
        when(configProperties.getStorage()).thenReturn(storage);

        assertThat(TurStorageConfig.resolveType(configProperties)).isEqualTo(TurStorageType.NONE);
    }
}
