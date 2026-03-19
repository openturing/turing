package com.viglet.turing.api.system;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.service.asset.TurMinioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API that exposes runtime system information such as application version,
 * database metadata, JVM memory, disk space and system properties.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@RestController
@RequestMapping("/api/system/info")
@Tag(name = "System Information", description = "System Information API")
public class TurSystemInfoAPI {

    private final DataSource dataSource;
    private final TurMinioService turMinioService;
    private final TurConfigProperties turConfigProperties;
    private final boolean mongoEnabled;
    private final String mongoUri;

    public TurSystemInfoAPI(DataSource dataSource,
                            TurMinioService turMinioService,
                            TurConfigProperties turConfigProperties,
                            @Value("${turing.mongodb.enabled:false}") boolean mongoEnabled,
                            @Value("${turing.mongodb.uri:mongodb://localhost:27017}") String mongoUri) {
        this.dataSource = dataSource;
        this.turMinioService = turMinioService;
        this.turConfigProperties = turConfigProperties;
        this.mongoEnabled = mongoEnabled;
        this.mongoUri = mongoUri;
    }

    @Operation(summary = "Get system information overview")
    @GetMapping
    public TurSystemInfoBean getSystemInfo() {
        return TurSystemInfoBean.builder()
                .appVersion(getAppVersion())
                .database(getDatabaseInfo())
                .memory(getMemoryInfo())
                .disk(getDiskInfo())
                .mongodb(getMongoDbInfo())
                .minio(getMinioInfo())
                .build();
    }

    @Operation(summary = "Get system variables (JVM system properties)")
    @GetMapping("/variables")
    public Map<String, String> getSystemVariables() {
        Map<String, String> variables = new LinkedHashMap<>();
        System.getProperties().stringPropertyNames().stream()
                .sorted()
                .forEach(key -> variables.put(key, System.getProperty(key)));
        return variables;
    }

    private String getAppVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    private TurSystemInfoBean.DatabaseInfo getDatabaseInfo() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            return new TurSystemInfoBean.DatabaseInfo(
                    meta.getDatabaseProductName(),
                    meta.getDatabaseProductVersion(),
                    meta.getDriverName(),
                    meta.getDriverVersion(),
                    meta.getURL(),
                    "UP"
            );
        } catch (Exception e) {
            return new TurSystemInfoBean.DatabaseInfo(
                    "Unknown", "Unknown", "Unknown", "Unknown", "Unknown", "DOWN"
            );
        }
    }

    private TurSystemInfoBean.MemoryInfo getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;

        long totalPhysicalMemory = -1;
        long freePhysicalMemory = -1;
        long totalSwap = -1;
        long freeSwap = -1;
        try {
            var osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean osMx) {
                totalPhysicalMemory = osMx.getTotalMemorySize();
                freePhysicalMemory = osMx.getFreeMemorySize();
                // On Windows, getTotalSwapSpaceSize() returns RAM + pagefile;
                // subtract physical RAM to get actual pagefile size.
                long reportedSwap = osMx.getTotalSwapSpaceSize();
                long reportedFreeSwap = osMx.getFreeSwapSpaceSize();
                if (reportedSwap > totalPhysicalMemory) {
                    totalSwap = reportedSwap - totalPhysicalMemory;
                    freeSwap = Math.max(0, reportedFreeSwap - freePhysicalMemory);
                } else {
                    totalSwap = reportedSwap;
                    freeSwap = reportedFreeSwap;
                }
            }
        } catch (Exception ignored) {
            // Not available on all JVMs
        }

        return new TurSystemInfoBean.MemoryInfo(
                maxMemory,
                totalMemory,
                usedMemory,
                freeMemory,
                totalPhysicalMemory,
                freePhysicalMemory,
                totalSwap,
                freeSwap
        );
    }

    private TurSystemInfoBean.ExternalServiceInfo getMongoDbInfo() {
        if (!mongoEnabled) {
            return new TurSystemInfoBean.ExternalServiceInfo(false, null, null, null);
        }
        String version = "Unknown";
        String status = "DOWN";
        try (var mongoClient = com.mongodb.client.MongoClients.create(mongoUri)) {
            org.bson.Document buildInfo = mongoClient.getDatabase("admin")
                    .runCommand(new org.bson.Document("buildInfo", 1));
            version = buildInfo.getString("version");
            status = "UP";
        } catch (Exception ignored) {
            // MongoDB may be unreachable
        }
        return new TurSystemInfoBean.ExternalServiceInfo(true, version, mongoUri, status);
    }

    private TurSystemInfoBean.ExternalServiceInfo getMinioInfo() {
        if (!turMinioService.isEnabled()) {
            return new TurSystemInfoBean.ExternalServiceInfo(false, null, null, null);
        }
        var minio = turConfigProperties.getMinio();
        String endpoint = minio != null ? minio.getEndpoint() : "";
        String version = "Unknown";
        String status = "DOWN";
        try {
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(endpoint + "/minio/health/cluster"))
                    .GET().build();
            var response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
            String serverHeader = response.headers().firstValue("Server").orElse("");
            if (!serverHeader.isBlank()) {
                version = serverHeader;
            }
            status = response.statusCode() < 500 ? "UP" : "DOWN";
        } catch (Exception ignored) {
            // MinIO may be unreachable
        }
        return new TurSystemInfoBean.ExternalServiceInfo(true, version, endpoint, status);
    }

    private TurSystemInfoBean.DiskInfo getDiskInfo() {
        File root = new File(".");
        return new TurSystemInfoBean.DiskInfo(
                root.getTotalSpace(),
                root.getUsableSpace(),
                root.getTotalSpace() - root.getUsableSpace()
        );
    }
}
