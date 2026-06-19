package com.viglet.turing.api.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.genai.tool.TurSystemInfoToolService;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.service.storage.TurStorageType;
import com.viglet.turing.system.TurLlmSummaryService;

/**
 * Tests for TurSystemInfoAPI.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSystemInfoAPITest {

    @Mock
    private DataSource dataSource;

    @Mock
    private TurStorageService storageService;

    @Mock
    private TurConfigProperties turConfigProperties;

    @Mock
    private TurLlmSummaryService llmSummaryService;

    @Mock
    private TurSystemInfoToolService systemInfoToolService;

    private TurSystemInfoAPI api;

    @BeforeEach
    void setUp() {
        api = new TurSystemInfoAPI(dataSource, storageService, turConfigProperties,
                llmSummaryService, systemInfoToolService,
                false, "mongodb://localhost:27017");
    }

    @Test
    void getSystemInfoShouldReturnBean() throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("H2");
        when(meta.getDatabaseProductVersion()).thenReturn("2.0");
        when(meta.getDriverName()).thenReturn("H2 Driver");
        when(meta.getDriverVersion()).thenReturn("2.0.0");
        when(meta.getURL()).thenReturn("jdbc:h2:mem:test");
        when(storageService.isEnabled()).thenReturn(false);
        when(storageService.getType()).thenReturn(TurStorageType.NONE);

        TurSystemInfoBean result = api.getSystemInfo();

        assertThat(result).isNotNull();
        assertThat(result.getAppVersion()).isNotNull();
        assertThat(result.getDatabase()).isNotNull();
        assertThat(result.getDatabase().productName()).isEqualTo("H2");
        assertThat(result.getDatabase().status()).isEqualTo("UP");
        assertThat(result.getMemory()).isNotNull();
        assertThat(result.getMemory().maxMemory()).isGreaterThan(0);
        assertThat(result.getDisk()).isNotNull();
        assertThat(result.getDisk().totalSpace()).isGreaterThan(0);
    }

    @Test
    void getSystemInfoShouldReturnDownDbWhenConnectionFails() throws Exception {
        when(dataSource.getConnection()).thenThrow(new java.sql.SQLException("Connection refused"));
        when(storageService.isEnabled()).thenReturn(false);
        when(storageService.getType()).thenReturn(TurStorageType.NONE);

        TurSystemInfoBean result = api.getSystemInfo();

        assertThat(result.getDatabase().status()).isEqualTo("DOWN");
        assertThat(result.getDatabase().productName()).isEqualTo("Unknown");
    }

    @Test
    void getSystemInfoShouldReturnMongoDisabledWhenNotEnabled() throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("H2");
        when(meta.getDatabaseProductVersion()).thenReturn("2.0");
        when(meta.getDriverName()).thenReturn("H2 Driver");
        when(meta.getDriverVersion()).thenReturn("2.0.0");
        when(meta.getURL()).thenReturn("jdbc:h2:mem:test");
        when(storageService.isEnabled()).thenReturn(false);
        when(storageService.getType()).thenReturn(TurStorageType.NONE);

        TurSystemInfoBean result = api.getSystemInfo();

        assertThat(result.getMongodb()).isNotNull();
        assertThat(result.getMongodb().enabled()).isFalse();
    }

    @Test
    void getSystemInfoShouldReturnMinioDisabledWhenNotEnabled() throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("H2");
        when(meta.getDatabaseProductVersion()).thenReturn("2.0");
        when(meta.getDriverName()).thenReturn("H2 Driver");
        when(meta.getDriverVersion()).thenReturn("2.0.0");
        when(meta.getURL()).thenReturn("jdbc:h2:mem:test");
        when(storageService.isEnabled()).thenReturn(false);
        when(storageService.getType()).thenReturn(TurStorageType.NONE);

        TurSystemInfoBean result = api.getSystemInfo();

        assertThat(result.getStorage()).isNotNull();
        assertThat(result.getStorage().enabled()).isFalse();
    }

    @Test
    void getSystemVariablesShouldReturnNonEmptyMap() {
        var result = api.getSystemVariables();

        assertThat(result)
                .isNotEmpty()
                .containsKey("java.version");
    }

    @Test
    void getSystemVariablesShouldBeSorted() {
        var result = api.getSystemVariables();

        var keys = new java.util.ArrayList<>(result.keySet());
        var sorted = new java.util.ArrayList<>(keys);
        java.util.Collections.sort(sorted);

        assertThat(keys).isEqualTo(sorted);
    }

    @Test
    void memoryInfoShouldHavePositiveValues() throws Exception {
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(conn);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getDatabaseProductName()).thenReturn("H2");
        when(meta.getDatabaseProductVersion()).thenReturn("2.0");
        when(meta.getDriverName()).thenReturn("H2 Driver");
        when(meta.getDriverVersion()).thenReturn("2.0.0");
        when(meta.getURL()).thenReturn("jdbc:h2:mem:test");
        when(storageService.isEnabled()).thenReturn(false);
        when(storageService.getType()).thenReturn(TurStorageType.NONE);

        TurSystemInfoBean result = api.getSystemInfo();

        assertThat(result.getMemory().totalMemory()).isGreaterThan(0);
        assertThat(result.getMemory().freeMemory()).isGreaterThanOrEqualTo(0);
        assertThat(result.getMemory().usedMemory()).isGreaterThanOrEqualTo(0);
    }
}
