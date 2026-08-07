package com.viglet.turing.api.system;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.sn.dsl.eval.TurRankingLexiconProvider;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/system/cache")
@Tag(name = "Cache", description = "Application Cache API")
public class TurCacheAPI {

    private static final String SUCCESS = "success";
    private static final String RANKING_LEXICONS = "rankingLexicons";

    private final CacheManager cacheManager;
    private final TurRankingLexiconProvider rankingLexiconProvider;

    public TurCacheAPI(CacheManager cacheManager,
            TurRankingLexiconProvider rankingLexiconProvider) {
        this.cacheManager = cacheManager;
        this.rankingLexiconProvider = rankingLexiconProvider;
    }

    @Operation(summary = "List all application caches")
    @GetMapping
    public Map<String, Object> listCaches() {
        List<String> cacheNames = cacheManager.getCacheNames().stream().sorted().toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(SUCCESS, true);
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
        result.put(SUCCESS, true);
        result.put("clearedCaches", cacheNames.size());
        result.put("cacheNames", cacheNames);
        // T942 / §LVIII.7 — the compiled ranking lexicons are held in a bean, not by
        // the CacheManager, so "clear all caches" used to leave them standing. An
        // admin who clears everything and still sees stale ranking vocabulary has no
        // way left to tell the difference between a cache and a bug.
        result.put(RANKING_LEXICONS, rankingLexiconProvider.reload());
        return result;
    }

    /**
     * T942 / §LVIII.7 — recompile the copilot's ranking lexicons alone, so an
     * operator who edited a file under
     * {@code turing.genai.copilot.ranking.lexicon-dir} does not have to flush every
     * other cache (or restart) to see it take effect.
     */
    @Operation(summary = "Reload the copilot ranking lexicons from resources and lexicon-dir")
    @PostMapping("/ranking-lexicons/reload")
    public Map<String, Object> reloadRankingLexicons() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(SUCCESS, true);
        result.put(RANKING_LEXICONS, rankingLexiconProvider.reload());
        return result;
    }
}
