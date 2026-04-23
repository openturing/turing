package com.viglet.turing.api.marketplace;

import com.viglet.turing.exchange.TurImportExchange;
import com.viglet.turing.exchange.sn.TurSNSiteContentExchangeService;
import com.viglet.turing.properties.TurConfigProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Marketplace API — lists available packages and imports them from a remote URL.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@RestController
@RequestMapping("/api/marketplace")
@Tag(name = "Marketplace", description = "Browse and import pre-built packages")
public class TurMarketplaceAPI {

    private static final String FALLBACK_CATALOG = "marketplace.json";

    private final TurConfigProperties configProperties;
    private final TurImportExchange turImportExchange;
    private final TurSNSiteContentExchangeService contentExchangeService;

    public TurMarketplaceAPI(TurConfigProperties configProperties,
                             TurImportExchange turImportExchange,
                             TurSNSiteContentExchangeService contentExchangeService) {
        this.configProperties = configProperties;
        this.turImportExchange = turImportExchange;
        this.contentExchangeService = contentExchangeService;
    }

    public record TurMarketplaceItem(
            String id,
            String title,
            String description,
            String version,
            String author,
            String category,
            List<String> tags,
            String icon,
            String downloadUrl,
            String readmeUrl,
            boolean hasContent,
            boolean hasTemplate
    ) {}

    @Operation(summary = "List marketplace packages")
    @GetMapping
    public ResponseEntity<List<TurMarketplaceItem>> list() {
        if (!isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        List<TurMarketplaceItem> items = fetchCatalog();
        return ResponseEntity.ok(items);
    }

    @Operation(summary = "Fetch the README content of a marketplace package (proxied to avoid CORS)")
    @GetMapping("/readme")
    public ResponseEntity<String> readme(@RequestParam String url) {
        if (!isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        if (!isSafeReadmeUrl(url)) {
            return ResponseEntity.badRequest().body("Invalid README URL");
        }
        try {
            byte[] body = downloadUrl(url);
            return ResponseEntity.ok()
                    .header("Content-Type", "text/markdown; charset=UTF-8")
                    .body(new String(body, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Could not fetch README from '{}': {}", url, e.getMessage());
            return ResponseEntity.status(502).body("Could not load README: " + e.getMessage());
        }
    }

    private boolean isSafeReadmeUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return false;
            }
            String host = uri.getHost();
            return host != null && !host.isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Operation(summary = "Import a package from the marketplace by downloading its ZIP")
    @PostMapping("/import")
    public ResponseEntity<TurImportExchange.ImportResult> importPackage(
            @RequestParam String downloadUrl,
            @RequestParam(defaultValue = "false") boolean includeContent,
            @RequestParam(defaultValue = "false") boolean includeTemplate,
            @RequestParam(required = false) String taskId,
            @RequestParam(defaultValue = "false") boolean overwrite) {
        if (!isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] zipBytes = downloadZip(downloadUrl);
            var multipartFile = new MockMultipartFile("file", "marketplace.zip",
                    "application/zip", zipBytes);
            var result = turImportExchange.importFromMultipartFile(
                    multipartFile, includeContent, includeTemplate, taskId, overwrite);
            if (result.error() != null) {
                return ResponseEntity.badRequest().body(result);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Marketplace import failed for URL '{}': {}", downloadUrl, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(TurImportExchange.ImportResult.error(e.getMessage()));
        }
    }

    @Operation(summary = "Stream marketplace install progress (content indexing phase) via SSE")
    @GetMapping(value = "/import/progress/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter importProgress(@PathVariable String taskId) {
        SseEmitter emitter = new SseEmitter(600_000L);
        Thread.ofVirtual().start(() -> {
            try {
                boolean completed = false;
                while (!completed) {
                    var progress = contentExchangeService.getProgress(taskId);
                    if (progress.isPresent()) {
                        var p = progress.get();
                        emitter.send(SseEmitter.event()
                                .name("progress")
                                .data(Map.of(
                                        "totalDocuments", p.totalDocuments(),
                                        "processedDocuments", p.processedDocuments(),
                                        "percentage", p.percentage(),
                                        "currentLocale", p.currentLocale(),
                                        "phase", p.phase(),
                                        "estimatedRemainingMillis", p.estimatedRemainingMillis()
                                )));
                        if ("completed".equals(p.phase())) {
                            completed = true;
                            contentExchangeService.removeProgress(taskId);
                        }
                    }
                    if (!completed) {
                        Thread.sleep(500);
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    /**
     * Downloads the package ZIP and returns any existing SN sites that would be
     * overwritten (matched by ID), plus whether it contains content and/or a template.
     * The frontend uses this to prompt the user before actually installing.
     *
     * @since 2026.2.4
     */
    @Operation(summary = "Inspect a marketplace package before installing")
    @GetMapping("/inspect")
    public ResponseEntity<java.util.Map<String, Object>> inspectPackage(@RequestParam String downloadUrl) {
        if (!isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] zipBytes = downloadZip(downloadUrl);
            var multipartFile = new MockMultipartFile("file", "marketplace.zip",
                    "application/zip", zipBytes);
            return ResponseEntity.ok(turImportExchange.checkZip(multipartFile));
        } catch (Exception e) {
            log.error("Marketplace inspect failed for URL '{}': {}", downloadUrl, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    private boolean isEnabled() {
        var marketplace = configProperties.getMarketplace();
        return marketplace != null && marketplace.isEnabled();
    }

    private List<TurMarketplaceItem> fetchCatalog() {
        ObjectMapper mapper = JsonMapper.builder().build();
        var marketplace = configProperties.getMarketplace();
        if (marketplace != null && marketplace.getUrl() != null && !marketplace.getUrl().isBlank()) {
            try {
                byte[] body = downloadUrl(marketplace.getUrl());
                return mapper.readValue(body, new TypeReference<>() {});
            } catch (Exception e) {
                log.warn("Could not fetch external marketplace catalog from '{}', falling back to bundled: {}",
                        marketplace.getUrl(), e.getMessage());
            }
        }
        return loadFallbackCatalog(mapper);
    }

    private List<TurMarketplaceItem> loadFallbackCatalog(ObjectMapper mapper) {
        try (InputStream is = new ClassPathResource(FALLBACK_CATALOG).getInputStream()) {
            return mapper.readValue(is, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to load bundled marketplace catalog: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private byte[] downloadUrl(String url) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new RuntimeException("HTTP " + response.statusCode());
            }
            return response.body();
        }
    }

    private byte[] downloadZip(String url) throws Exception {
        return downloadUrl(url);
    }
}
