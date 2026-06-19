package com.viglet.turing.genai.tool;

import javax.sql.DataSource;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.service.storage.TurStorageType;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * Tool calling service for system environment status via LLM.
 * Provides tools to check application health, database, memory, disk,
 * and external services status.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
@Slf4j
@Service
public class TurSystemInfoToolService {

    // --- S1192: extracted duplicated literals ---
    private static final String STATUS = "Status: ";
    private static final String ENDPOINT = "Endpoint: ";


    private static final String USED_PREFIX = "Used: ";
    private static final String FREE_PREFIX = "Free: ";
    private static final String STATUS_UP = "Status: UP\n";
    private static final String STATUS_DOWN = "Status: DOWN\n";
    private static final String STATUS_DOWN_SECTION = "Status: DOWN\n\n";
    private static final String ERROR_PREFIX = "Error: ";

    private final DataSource dataSource;
    private final TurStorageService storageService;
    private final TurConfigProperties turConfigProperties;
    private final boolean mongoEnabled;
    private final String mongoUri;

    public TurSystemInfoToolService(DataSource dataSource,
                                    TurStorageService storageService,
                                    TurConfigProperties turConfigProperties,
                                    @Value("${turing.mongodb.enabled:false}") boolean mongoEnabled,
                                    @Value("${turing.mongodb.uri:mongodb://localhost:27017}") String mongoUri) {
        this.dataSource = dataSource;
        this.storageService = storageService;
        this.turConfigProperties = turConfigProperties;
        this.mongoEnabled = mongoEnabled;
        this.mongoUri = mongoUri;
    }

    @Tool(name = "get_system_status", description = ".")
    public String getSystemStatus() {
        log.info("[SystemInfo Tool] get_system_status called");
        StringBuilder sb = new StringBuilder();
        sb.append("=== Viglet Turing ES — System Status ===\n\n");

        appendAppVersion(sb);
        appendDatabaseStatus(sb);
        appendMemoryStatus(sb);
        appendDiskStatus(sb);
        appendMongoDbStatus(sb);
        appendStorageStatus(sb);
        appendJvmInfo(sb);

        return sb.toString();
    }

    @Tool(name = "get_memory_details", description = ".")
    public String getMemoryDetails() {
        log.info("[SystemInfo Tool] get_memory_details called");
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;

        StringBuilder sb = new StringBuilder();
        sb.append("=== Memory Details ===\n\n");
        sb.append("--- JVM Heap ---\n");
        sb.append("Max Heap: ").append(formatBytes(maxMemory)).append("\n");
        sb.append("Allocated: ").append(formatBytes(totalMemory)).append("\n");
        sb.append(USED_PREFIX).append(formatBytes(usedMemory)).append("\n");
        sb.append(FREE_PREFIX).append(formatBytes(freeMemory)).append("\n");
        sb.append("Usage: ").append(percent(usedMemory, maxMemory)).append("%\n");

        try {
            var osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean osMx) {
                long totalPhysical = osMx.getTotalMemorySize();
                long freePhysical = osMx.getFreeMemorySize();
                long usedPhysical = totalPhysical - freePhysical;

                sb.append("\n--- Physical Memory ---\n");
                sb.append("Total: ").append(formatBytes(totalPhysical)).append("\n");
                sb.append(USED_PREFIX).append(formatBytes(usedPhysical)).append("\n");
                sb.append(FREE_PREFIX).append(formatBytes(freePhysical)).append("\n");
                sb.append("Usage: ").append(percent(usedPhysical, totalPhysical)).append("%\n");

                long reportedSwap = osMx.getTotalSwapSpaceSize();
                long reportedFreeSwap = osMx.getFreeSwapSpaceSize();
                long totalSwap = reportedSwap > totalPhysical
                        ? reportedSwap - totalPhysical : reportedSwap;
                long freeSwap = reportedSwap > totalPhysical
                        ? Math.max(0, reportedFreeSwap - freePhysical) : reportedFreeSwap;
                long usedSwap = totalSwap - freeSwap;

                sb.append("\n--- Swap ---\n");
                sb.append("Total: ").append(formatBytes(totalSwap)).append("\n");
                sb.append(USED_PREFIX).append(formatBytes(usedSwap)).append("\n");
                sb.append(FREE_PREFIX).append(formatBytes(freeSwap)).append("\n");

                sb.append("\n--- CPU ---\n");
                sb.append("Available Processors: ").append(osMx.getAvailableProcessors()).append("\n");
                double cpuLoad = osMx.getCpuLoad();
                if (cpuLoad >= 0) {
                    sb.append("System CPU Load: ").append(String.format("%.1f", cpuLoad * 100)).append("%\n");
                }
                double processLoad = osMx.getProcessCpuLoad();
                if (processLoad >= 0) {
                    sb.append("Process CPU Load: ").append(String.format("%.1f", processLoad * 100)).append("%\n");
                }
            }
        } catch (Exception e) {
            sb.append("\nOS-level memory details not available.\n");
        }

        return sb.toString();
    }

    @Tool(name = "get_database_status", description = ".")
    public String getDatabaseStatus() {
        log.info("[SystemInfo Tool] get_database_status called");
        StringBuilder sb = new StringBuilder();
        sb.append("=== Database Status ===\n\n");
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            sb.append(STATUS_UP);
            sb.append("Product: ").append(meta.getDatabaseProductName()).append("\n");
            sb.append("Version: ").append(meta.getDatabaseProductVersion()).append("\n");
            sb.append("Driver: ").append(meta.getDriverName()).append(" ").append(meta.getDriverVersion()).append("\n");
            sb.append("URL: ").append(meta.getURL()).append("\n");
            sb.append("User: ").append(meta.getUserName()).append("\n");
            sb.append("Max Connections: ").append(meta.getMaxConnections()).append("\n");
            sb.append("Read-only: ").append(conn.isReadOnly() ? "yes" : "no").append("\n");
            sb.append("Auto-commit: ").append(conn.getAutoCommit() ? "yes" : "no").append("\n");
        } catch (Exception e) {
            sb.append(STATUS_DOWN);
            sb.append(ERROR_PREFIX).append(e.getMessage()).append("\n");
        }
        return sb.toString();
    }

    @Tool(name = "get_external_services_status", description = ".")
    public String getExternalServicesStatus() {
        log.info("[SystemInfo Tool] get_external_services_status called");
        StringBuilder sb = new StringBuilder();
        sb.append("=== External Services Status ===\n\n");
        appendExternalMongoStatus(sb);
        appendExternalStorageStatus(sb);
        return sb.toString();
    }

    private void appendExternalMongoStatus(StringBuilder sb) {
        sb.append("--- MongoDB ---\n");
        if (!mongoEnabled) {
            sb.append("Status: DISABLED\n");
            return;
        }
        try (var mongoClient = com.mongodb.client.MongoClients.create(mongoUri)) {
            org.bson.Document buildInfo = mongoClient.getDatabase("admin")
                    .runCommand(new org.bson.Document("buildInfo", 1));
            sb.append(STATUS_UP);
            sb.append("Version: ").append(buildInfo.getString("version")).append("\n");
            sb.append("URI: ").append(mongoUri).append("\n");
        } catch (Exception e) {
            sb.append(STATUS_DOWN);
            sb.append("URI: ").append(mongoUri).append("\n");
            sb.append(ERROR_PREFIX).append(e.getMessage()).append("\n");
        }
    }

    private void appendExternalStorageStatus(StringBuilder sb) {
        sb.append("\n--- Storage (").append(storageService.getType()).append(") ---\n");
        if (!storageService.isEnabled()) {
            sb.append("Status: DISABLED\n");
        } else if (storageService.getType() == TurStorageType.MINIO) {
            appendExternalMinioStatus(sb);
        } else {
            var storage = turConfigProperties.getStorage();
            String path = storage != null && storage.getFilesystem() != null && storage.getFilesystem().getPath() != null
                    ? storage.getFilesystem().getPath() : "./store/assets";
            sb.append("Path: ").append(path).append("\n");
            sb.append(STATUS).append(new java.io.File(path).isDirectory() ? "UP" : "DOWN").append("\n");
        }
    }

    private void appendExternalMinioStatus(StringBuilder sb) {
        var minio = turConfigProperties.getStorage() != null ? turConfigProperties.getStorage().getMinio() : null;
        String endpoint = minio != null ? minio.getEndpoint() : "";
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            // URI.create + .uri() validate the scheme and throw on a blank/relative
            // endpoint — keep them inside the try so that surfaces as Status: DOWN.
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(endpoint + "/minio/health/cluster"))
                    .GET().build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            String status = response.statusCode() < 500 ? "UP" : "DOWN";
            sb.append(STATUS).append(status).append("\n");
            sb.append(ENDPOINT).append(endpoint).append("\n");
            response.headers().firstValue("Server").ifPresent(
                    server -> sb.append("Server: ").append(server).append("\n"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sb.append(STATUS_DOWN);
            sb.append(ENDPOINT).append(endpoint).append("\n");
            sb.append(ERROR_PREFIX).append(e.getMessage()).append("\n");
        } catch (Exception e) {
            sb.append(STATUS_DOWN);
            sb.append(ENDPOINT).append(endpoint).append("\n");
            sb.append(ERROR_PREFIX).append(e.getMessage()).append("\n");
        }
    }

    private void appendAppVersion(StringBuilder sb) {
        String version = getClass().getPackage().getImplementationVersion();
        sb.append("App Version: ").append(version != null ? version : "dev").append("\n\n");
    }

    private void appendDatabaseStatus(StringBuilder sb) {
        sb.append("--- Database ---\n");
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            sb.append(STATUS_UP);
            sb.append("Product: ").append(meta.getDatabaseProductName())
                    .append(" ").append(meta.getDatabaseProductVersion()).append("\n");
        } catch (Exception e) {
            sb.append("Status: DOWN (").append(e.getMessage()).append(")\n");
        }
        sb.append("\n");
    }

    private void appendMemoryStatus(StringBuilder sb) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        sb.append("--- JVM Memory ---\n");
        sb.append("Heap: ").append(formatBytes(usedMemory)).append(" / ").append(formatBytes(maxMemory));
        sb.append(" (").append(percent(usedMemory, maxMemory)).append("% used)\n\n");
    }

    private void appendDiskStatus(StringBuilder sb) {
        File root = new File(".");
        long total = root.getTotalSpace();
        long usable = root.getUsableSpace();
        long used = total - usable;
        sb.append("--- Disk ---\n");
        sb.append(USED_PREFIX).append(formatBytes(used)).append(" / ").append(formatBytes(total));
        sb.append(" (").append(percent(used, total)).append("% used)\n");
        sb.append(FREE_PREFIX).append(formatBytes(usable)).append("\n\n");
    }

    private void appendMongoDbStatus(StringBuilder sb) {
        sb.append("--- MongoDB ---\n");
        if (!mongoEnabled) {
            sb.append("Status: DISABLED\n\n");
            return;
        }
        try (var mongoClient = com.mongodb.client.MongoClients.create(mongoUri)) {
            org.bson.Document buildInfo = mongoClient.getDatabase("admin")
                    .runCommand(new org.bson.Document("buildInfo", 1));
            sb.append("Status: UP (v").append(buildInfo.getString("version")).append(")\n\n");
        } catch (Exception e) {
            sb.append(STATUS_DOWN_SECTION);
        }
    }

    private void appendStorageStatus(StringBuilder sb) {
        sb.append("--- Storage (").append(storageService.getType()).append(") ---\n");
        if (!storageService.isEnabled()) {
            sb.append("Status: DISABLED\n\n");
            return;
        }
        if (storageService.getType() == TurStorageType.MINIO) {
            appendMinioHealthCheck(sb);
        } else {
            var storage = turConfigProperties.getStorage();
            String path = storage != null && storage.getFilesystem() != null && storage.getFilesystem().getPath() != null
                    ? storage.getFilesystem().getPath() : "./store/assets";
            sb.append("Path: ").append(path).append("\n");
            sb.append(STATUS).append(new java.io.File(path).isDirectory() ? "UP" : "DOWN").append("\n\n");
        }
    }

    private void appendMinioHealthCheck(StringBuilder sb) {
        var minio = turConfigProperties.getStorage() != null ? turConfigProperties.getStorage().getMinio() : null;
        String endpoint = minio != null ? minio.getEndpoint() : "";
        try (var client = java.net.http.HttpClient.newHttpClient()) {
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(endpoint + "/minio/health/cluster"))
                    .GET().build();
            var response = client
                    .send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            sb.append(STATUS).append(response.statusCode() < 500 ? "UP" : "DOWN").append("\n\n");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sb.append(STATUS_DOWN_SECTION);
        } catch (Exception e) {
            sb.append(STATUS_DOWN_SECTION);
        }
    }

    private void appendJvmInfo(StringBuilder sb) {
        sb.append("--- JVM ---\n");
        sb.append("Java: ").append(System.getProperty("java.version")).append("\n");
        sb.append("VM: ").append(System.getProperty("java.vm.name")).append("\n");
        sb.append("OS: ").append(System.getProperty("os.name"))
                .append(" ").append(System.getProperty("os.version")).append("\n");
        sb.append("Processors: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "N/A";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }

    private long percent(long used, long total) {
        if (total <= 0) return 0;
        return Math.round((double) used / total * 100);
    }
}
