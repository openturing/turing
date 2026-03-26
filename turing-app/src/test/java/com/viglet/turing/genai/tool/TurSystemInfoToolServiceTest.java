package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.service.asset.TurMinioService;

/**
 * Tests for TurSystemInfoToolService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurSystemInfoToolServiceTest {

    @Mock
    private DataSource dataSource;
    @Mock
    private TurMinioService turMinioService;
    @Mock
    private TurConfigProperties turConfigProperties;

    private TurSystemInfoToolService service;

    @BeforeEach
    void setUp() {
        service = new TurSystemInfoToolService(
                dataSource, turMinioService, turConfigProperties, false, "mongodb://localhost:27017");
    }

    @ParameterizedTest
    @CsvSource({
            "-1, N/A",
            "0, 0 B",
            "500, 500 B",
            "1023, 1023 B"
    })
    void formatBytesShouldFormatSmallValues(long bytes, String expected) throws Exception {
        Method method = TurSystemInfoToolService.class.getDeclaredMethod("formatBytes", long.class);
        method.setAccessible(true);
        assertThat(method.invoke(service, bytes)).isEqualTo(expected);
    }

    @Test
    void formatBytesShouldFormatKilobytes() throws Exception {
        Method method = TurSystemInfoToolService.class.getDeclaredMethod("formatBytes", long.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, 2048L);
        assertThat(result).matches("2[.,]0 KB");
    }

    @Test
    void formatBytesShouldFormatMegabytes() throws Exception {
        Method method = TurSystemInfoToolService.class.getDeclaredMethod("formatBytes", long.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, 5_242_880L);
        assertThat(result).matches("5[.,]0 MB");
    }

    @Test
    void formatBytesShouldFormatGigabytes() throws Exception {
        Method method = TurSystemInfoToolService.class.getDeclaredMethod("formatBytes", long.class);
        method.setAccessible(true);
        String result = (String) method.invoke(service, 2_147_483_648L);
        assertThat(result).matches("2[.,]00 GB");
    }

    @ParameterizedTest
    @CsvSource({
            "0, 100, 0",
            "50, 100, 50",
            "100, 100, 100",
            "0, 0, 0",
            "75, 200, 38"
    })
    void percentShouldCalculateCorrectly(long used, long total, long expected) throws Exception {
        Method method = TurSystemInfoToolService.class.getDeclaredMethod("percent", long.class, long.class);
        method.setAccessible(true);
        assertThat(method.invoke(service, used, total)).isEqualTo(expected);
    }

    @Test
    void percentShouldReturnZeroForNegativeTotal() throws Exception {
        Method method = TurSystemInfoToolService.class.getDeclaredMethod("percent", long.class, long.class);
        method.setAccessible(true);
        assertThat(method.invoke(service, 50L, -1L)).isEqualTo(0L);
    }

    @Test
    void getDatabaseStatusShouldReturnUpWhenConnected() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("H2");
        when(metadata.getDatabaseProductVersion()).thenReturn("2.2.224");
        when(metadata.getDriverName()).thenReturn("H2 JDBC Driver");
        when(metadata.getDriverVersion()).thenReturn("2.2.224");
        when(metadata.getURL()).thenReturn("jdbc:h2:mem:test");
        when(metadata.getUserName()).thenReturn("SA");
        when(metadata.getMaxConnections()).thenReturn(0);
        when(connection.isReadOnly()).thenReturn(false);
        when(connection.getAutoCommit()).thenReturn(true);

        String result = service.getDatabaseStatus();
        assertThat(result)
                .contains("Status: UP")
                .contains("Product: H2")
                .contains("Version: 2.2.224")
                .contains("jdbc:h2:mem:test");
    }

    @Test
    void getDatabaseStatusShouldReturnDownOnException() throws Exception {
        when(dataSource.getConnection()).thenThrow(new RuntimeException("Connection refused"));

        String result = service.getDatabaseStatus();
        assertThat(result)
                .contains("Status: DOWN")
                .contains("Connection refused");
    }

    @Test
    void getMemoryDetailsShouldContainHeapInfo() {
        String result = service.getMemoryDetails();
        assertThat(result)
                .contains("Memory Details")
                .contains("JVM Heap")
                .contains("Max Heap:")
                .contains("Allocated:")
                .contains("Used:")
                .contains("Free:")
                .contains("Usage:");
    }

    @Test
    void getExternalServicesStatusShouldShowMongoDisabled() {
        String result = service.getExternalServicesStatus();
        assertThat(result)
                .contains("MongoDB")
                .contains("Status: DISABLED");
    }

    @Test
    void getExternalServicesStatusShouldShowMinioDisabled() {
        when(turMinioService.isEnabled()).thenReturn(false);
        String result = service.getExternalServicesStatus();
        assertThat(result)
                .contains("MinIO")
                .contains("Status: DISABLED");
    }

    @Test
    void getSystemStatusShouldContainAllSections() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("H2");
        when(metadata.getDatabaseProductVersion()).thenReturn("2.2");
        when(turMinioService.isEnabled()).thenReturn(false);

        String result = service.getSystemStatus();
        assertThat(result)
                .contains("Viglet Turing ES")
                .contains("Database")
                .contains("JVM Memory")
                .contains("Disk")
                .contains("MongoDB")
                .contains("MinIO")
                .contains("JVM")
                .contains("Java:");
    }
}
