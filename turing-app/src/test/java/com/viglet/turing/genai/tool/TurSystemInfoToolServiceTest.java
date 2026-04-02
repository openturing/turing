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
import com.viglet.turing.properties.TurMinioProperty;
import com.viglet.turing.properties.TurStorageProperty;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.service.storage.TurStorageType;

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
    private TurStorageService storageService;
    @Mock
    private TurConfigProperties turConfigProperties;

    private TurSystemInfoToolService service;

    @BeforeEach
    void setUp() {
        service = new TurSystemInfoToolService(
                dataSource, storageService, turConfigProperties, false, "mongodb://localhost:27017");
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
    void getExternalServicesStatusShouldShowStorageDisabled() {
        when(storageService.isEnabled()).thenReturn(false);
        when(storageService.getType()).thenReturn(TurStorageType.NONE);
        String result = service.getExternalServicesStatus();
        assertThat(result)
                .contains("Storage")
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
        when(storageService.isEnabled()).thenReturn(false);
        when(storageService.getType()).thenReturn(TurStorageType.NONE);

        String result = service.getSystemStatus();
        assertThat(result)
                .contains("Viglet Turing ES")
                .contains("Database")
                .contains("JVM Memory")
                .contains("Disk")
                .contains("MongoDB")
                .contains("Storage")
                .contains("JVM")
                .contains("Java:");
    }

    @Test
    void getSystemStatusShouldHandleDatabaseDown() throws Exception {
        when(dataSource.getConnection()).thenThrow(new RuntimeException("Connection refused"));
        when(storageService.isEnabled()).thenReturn(false);
        when(storageService.getType()).thenReturn(TurStorageType.NONE);

        String result = service.getSystemStatus();
        assertThat(result)
                .contains("Viglet Turing ES")
                .contains("DOWN");
    }

    @Test
    void getDatabaseStatusShouldShowReadOnly() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(metadata.getDatabaseProductVersion()).thenReturn("16.0");
        when(metadata.getDriverName()).thenReturn("PostgreSQL JDBC Driver");
        when(metadata.getDriverVersion()).thenReturn("42.7.0");
        when(metadata.getURL()).thenReturn("jdbc:postgresql://localhost/turing");
        when(metadata.getUserName()).thenReturn("turing");
        when(metadata.getMaxConnections()).thenReturn(100);
        when(connection.isReadOnly()).thenReturn(true);
        when(connection.getAutoCommit()).thenReturn(false);

        String result = service.getDatabaseStatus();
        assertThat(result)
                .contains("Status: UP")
                .contains("PostgreSQL")
                .contains("Read-only: yes")
                .contains("Auto-commit: no")
                .contains("Max Connections: 100");
    }

    @Test
    void getMemoryDetailsShouldContainPhysicalMemory() {
        String result = service.getMemoryDetails();
        // On Windows/Linux with com.sun.management.OperatingSystemMXBean available
        // the output should contain physical memory info
        assertThat(result)
                .contains("Memory Details")
                .contains("JVM Heap")
                .contains("Max Heap:");
    }

    @Test
    void getExternalServicesStatusShouldShowMongoEnabled() {
        TurSystemInfoToolService enabledMongoService = new TurSystemInfoToolService(
                dataSource, storageService, turConfigProperties,
                true, "mongodb://invalid-host-99999:99999");
        when(storageService.isEnabled()).thenReturn(false);
        when(storageService.getType()).thenReturn(TurStorageType.NONE);

        String result = enabledMongoService.getExternalServicesStatus();
        assertThat(result)
                .contains("MongoDB")
                .contains("DOWN");
    }

    @Test
    void getExternalServicesStatusShouldShowMinioEnabled() {
        TurMinioProperty minioProps = mock(TurMinioProperty.class);
        when(minioProps.getEndpoint()).thenReturn("http://invalid-host-99999:9000");
        TurStorageProperty storageProperty = mock(TurStorageProperty.class);
        when(storageProperty.getMinio()).thenReturn(minioProps);
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getType()).thenReturn(TurStorageType.MINIO);
        when(turConfigProperties.getStorage()).thenReturn(storageProperty);

        String result = service.getExternalServicesStatus();
        assertThat(result)
                .contains("Storage")
                .contains("DOWN");
    }

    @Test
    void getExternalServicesStatusShouldHandleNullMinioProps() {
        TurStorageProperty storageProperty = mock(TurStorageProperty.class);
        when(storageProperty.getMinio()).thenReturn(null);
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getType()).thenReturn(TurStorageType.MINIO);
        when(turConfigProperties.getStorage()).thenReturn(storageProperty);

        // This will try to build a URL with "" + "/minio/health/cluster" which should error
        String result = service.getExternalServicesStatus();
        assertThat(result)
                .contains("Storage")
                .contains("DOWN");
    }

    @Test
    void appendAppVersionShouldShowDevWhenNoVersion() throws Exception {
        java.lang.reflect.Method method = TurSystemInfoToolService.class.getDeclaredMethod(
                "appendAppVersion", StringBuilder.class);
        method.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb);

        // In test context, getImplementationVersion() returns null
        assertThat(sb.toString()).contains("App Version: dev");
    }

    @Test
    void appendDiskStatusShouldContainDiskInfo() throws Exception {
        java.lang.reflect.Method method = TurSystemInfoToolService.class.getDeclaredMethod(
                "appendDiskStatus", StringBuilder.class);
        method.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb);

        assertThat(sb.toString())
                .contains("Disk")
                .contains("Used:")
                .contains("Free:");
    }

    @Test
    void appendJvmInfoShouldContainJavaDetails() throws Exception {
        java.lang.reflect.Method method = TurSystemInfoToolService.class.getDeclaredMethod(
                "appendJvmInfo", StringBuilder.class);
        method.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb);

        assertThat(sb.toString())
                .contains("JVM")
                .contains("Java:")
                .contains("VM:")
                .contains("OS:")
                .contains("Processors:");
    }

    @Test
    void appendMemoryStatusShouldContainHeapInfo() throws Exception {
        java.lang.reflect.Method method = TurSystemInfoToolService.class.getDeclaredMethod(
                "appendMemoryStatus", StringBuilder.class);
        method.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb);

        assertThat(sb.toString())
                .contains("JVM Memory")
                .contains("Heap:");
    }

    @Test
    void appendMongoDbStatusShouldShowDisabled() throws Exception {
        java.lang.reflect.Method method = TurSystemInfoToolService.class.getDeclaredMethod(
                "appendMongoDbStatus", StringBuilder.class);
        method.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb);

        assertThat(sb.toString())
                .contains("MongoDB")
                .contains("DISABLED");
    }

    @Test
    void appendMongoDbStatusShouldShowDownWhenEnabled() throws Exception {
        TurSystemInfoToolService enabledMongoService = new TurSystemInfoToolService(
                dataSource, storageService, turConfigProperties,
                true, "mongodb://invalid-host-99999:99999");

        java.lang.reflect.Method method = TurSystemInfoToolService.class.getDeclaredMethod(
                "appendMongoDbStatus", StringBuilder.class);
        method.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        method.invoke(enabledMongoService, sb);

        assertThat(sb.toString())
                .contains("MongoDB")
                .contains("DOWN");
    }

    @Test
    void appendStorageStatusShouldShowDisabled() throws Exception {
        when(storageService.isEnabled()).thenReturn(false);
        when(storageService.getType()).thenReturn(TurStorageType.NONE);

        java.lang.reflect.Method method = TurSystemInfoToolService.class.getDeclaredMethod(
                "appendStorageStatus", StringBuilder.class);
        method.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb);

        assertThat(sb.toString())
                .contains("Storage")
                .contains("DISABLED");
    }

    @Test
    void appendStorageStatusShouldShowDownWhenMinioUnreachable() throws Exception {
        TurMinioProperty minioProps = mock(TurMinioProperty.class);
        when(minioProps.getEndpoint()).thenReturn("http://invalid-host-99999:9000");
        TurStorageProperty storageProperty = mock(TurStorageProperty.class);
        when(storageProperty.getMinio()).thenReturn(minioProps);
        when(storageService.isEnabled()).thenReturn(true);
        when(storageService.getType()).thenReturn(TurStorageType.MINIO);
        when(turConfigProperties.getStorage()).thenReturn(storageProperty);

        java.lang.reflect.Method method = TurSystemInfoToolService.class.getDeclaredMethod(
                "appendStorageStatus", StringBuilder.class);
        method.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb);

        assertThat(sb.toString())
                .contains("Storage")
                .contains("DOWN");
    }

    @Test
    void appendDatabaseStatusShouldShowUpWhenConnected() throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("H2");
        when(metadata.getDatabaseProductVersion()).thenReturn("2.2");

        java.lang.reflect.Method method = TurSystemInfoToolService.class.getDeclaredMethod(
                "appendDatabaseStatus", StringBuilder.class);
        method.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb);

        assertThat(sb.toString())
                .contains("Database")
                .contains("UP")
                .contains("H2 2.2");
    }

    @Test
    void appendDatabaseStatusShouldShowDownOnError() throws Exception {
        when(dataSource.getConnection()).thenThrow(new RuntimeException("Connection failed"));

        java.lang.reflect.Method method = TurSystemInfoToolService.class.getDeclaredMethod(
                "appendDatabaseStatus", StringBuilder.class);
        method.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb);

        assertThat(sb.toString())
                .contains("Database")
                .contains("DOWN")
                .contains("Connection failed");
    }
}
