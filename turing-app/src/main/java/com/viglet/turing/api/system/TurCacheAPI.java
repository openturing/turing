package com.viglet.turing.api.system;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/system/cache")
@Tag(name = "Cache", description = "Application Cache API")
public class TurCacheAPI {

    private final CacheManager cacheManager;

    public TurCacheAPI(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Operation(summary = "List all application caches")
    @GetMapping
    public Map<String, Object> listCaches() {
        List<String> cacheNames = cacheManager.getCacheNames().stream().sorted().toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("totalCaches", cacheNames.size());
        result.put("cacheNames", cacheNames);
        return result;
    }

    @Operation(summary = "Clear all application caches")
    @PostMapping("/clear")
    public Map<String, Object> clearAllCaches() {
        List<String> cacheNames = cacheManager.getCacheNames().stream().sorted().toList();
        cacheNames.forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("clearedCaches", cacheNames.size());
        result.put("cacheNames", cacheNames);
        return result;
    }
}
