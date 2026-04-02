package com.viglet.turing.api.system;

import com.viglet.turing.genai.tool.TurSystemInfoToolService;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.service.storage.TurStorageType;
import com.viglet.turing.system.TurLlmSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    private static final String UNKNOWN = "Unknown";
    private static final String INSIGHTS_CACHE_KEY = "system-info-insights";
    private static final String INSIGHTS_SYSTEM_PROMPT = """
            You are a system administrator expert analyzing a **Viglet Turing ES** (Enterprise Search) application server.

            ## About the Platform
            Viglet Turing ES is a Java-based enterprise search platform that combines:
            - **Apache Solr** (or Elasticsearch/Lucene) as its search engine for indexing and querying content
            - **Spring Boot 4 + Java 21** as the application runtime
            - **Spring AI** for LLM integration (chat, RAG, embeddings)
            - **React + TypeScript** frontend served by the same JVM process
            - **H2** (dev) or **MySQL/PostgreSQL** (production) as the relational database for configuration, users, and metadata
            - **Apache Artemis** message queue for asynchronous document indexing

            ## About Optional Components
            - **MongoDB**: When enabled, used exclusively for **persisting application logs to disk** \
            (server logs, AEM connector logs, indexing operation logs). It is NOT the primary database. \
            If MongoDB is disabled, logs are only available via standard application logging (stdout/files). \
            When reviewing MongoDB, focus on connectivity and whether log persistence is needed for the workload.
            - **Storage (MinIO or Filesystem)**: Used for **asset management and SPA page hosting**. \
            MinIO provides S3-compatible object storage; Filesystem stores files locally under a configured path. \
            When storage type is "none", asset upload/download and SPA page features are disabled entirely. \
            When reviewing storage, consider whether the chosen backend fits the deployment (e.g., filesystem \
            is simpler for single-node, MinIO for distributed/cloud deployments).

            ## Analysis Instructions
            Analyze the runtime data provided and generate a comprehensive summary in Markdown format. \
            Include these sections:

            ## Health Summary
            Brief overall health assessment — is the system healthy? Any services down?

            ## Resource Usage
            Evaluate JVM heap, physical RAM, swap, and disk usage. Flag high utilization or imbalanced allocation \
            (e.g., JVM heap too small relative to available RAM, or swap being used heavily).

            ## Configuration Review
            Review the database setup (H2 vs production DB), storage backend choice, and optional services. \
            Flag anything unusual for a production environment (e.g., H2 in production, storage disabled, etc.).

            ## Warnings
            Any concerning metrics, potential issues, or misconfigurations that need attention.

            ## Optimization Suggestions
            Actionable recommendations to improve performance, reliability, or resource usage. \
            Be specific — suggest concrete values or changes when possible (e.g., "increase -Xmx to 4G", \
            "switch from H2 to PostgreSQL for production", "enable storage for asset management").

            Be concise but insightful. Use bullet points where appropriate. \
            Tailor suggestions to the actual data — avoid generic advice that doesn't match the metrics.""";

    private final DataSource dataSource;
    private final TurStorageService storageService;
    private final TurConfigProperties turConfigProperties;
    private final TurLlmSummaryService llmSummaryService;
    private final TurSystemInfoToolService systemInfoToolService;
    private final boolean mongoEnabled;
    private final String mongoUri;

    public TurSystemInfoAPI(DataSource dataSource,
                            TurStorageService storageService,
                            TurConfigProperties turConfigProperties,
                            TurLlmSummaryService llmSummaryService,
                            TurSystemInfoToolService systemInfoToolService,
                            @Value("${turing.mongodb.enabled:false}") boolean mongoEnabled,
                            @Value("${turing.mongodb.uri:mongodb://localhost:27017}") String mongoUri) {
        this.dataSource = dataSource;
        this.storageService = storageService;
        this.turConfigProperties = turConfigProperties;
        this.llmSummaryService = llmSummaryService;
        this.systemInfoToolService = systemInfoToolService;
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
                .storage(getStorageInfo())
                .storageType(storageService.getType().name().toLowerCase())
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

    @Operation(summary = "Check if AI insights are available")
    @GetMapping("/insights/available")
    public ResponseEntity<Boolean> isInsightsAvailable() {
        return ResponseEntity.ok(llmSummaryService.isAvailable());
    }

    @Operation(summary = "Generate AI insights for system information")
    @GetMapping("/insights")
    public ResponseEntity<TurLlmSummaryService.SummaryResult> getInsights(
            @RequestParam(defaultValue = "false") boolean regenerate) {
        String systemData = systemInfoToolService.getSystemStatus();
        String userData = systemData
                + "\nPlease analyze all this data and provide a comprehensive summary with suggestions.";

        TurLlmSummaryService.SummaryResult result = llmSummaryService.generate(
                INSIGHTS_CACHE_KEY, userData, INSIGHTS_SYSTEM_PROMPT, regenerate);

        if (!result.success() && result.content() == null && result.error() != null
                && result.error().startsWith("No default LLM")) {
            return ResponseEntity.badRequest().body(result);
        }

        return ResponseEntity.ok(result);
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
                    UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN, "DOWN"
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
        String version = UNKNOWN;
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

    private TurSystemInfoBean.ExternalServiceInfo getStorageInfo() {
        if (!storageService.isEnabled()) {
            return new TurSystemInfoBean.ExternalServiceInfo(false, null, null, null);
        }
        if (storageService.getType() == TurStorageType.MINIO) {
            return getMinioHealthInfo();
        }
        // Filesystem storage
        var storage = turConfigProperties.getStorage();
        String path = storage != null && storage.getFilesystem() != null && storage.getFilesystem().getPath() != null
                ? storage.getFilesystem().getPath() : "./store/assets";
        java.io.File dir = new java.io.File(path);
        String status = dir.isDirectory() ? "UP" : "DOWN";
        return new TurSystemInfoBean.ExternalServiceInfo(true, "filesystem", path, status);
    }

    private TurSystemInfoBean.ExternalServiceInfo getMinioHealthInfo() {
        var minio = turConfigProperties.getStorage() != null ? turConfigProperties.getStorage().getMinio() : null;
        String endpoint = minio != null ? minio.getEndpoint() : "";
        String version = UNKNOWN;
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
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
