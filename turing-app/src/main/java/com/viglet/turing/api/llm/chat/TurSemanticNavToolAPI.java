package com.viglet.turing.api.llm.chat;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.tool.TurDslToolService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2/llm/tool")
@Tag(name = "LLM Tool Testing", description = "REST endpoints to test LLM DSL tool services directly")
public class TurSemanticNavToolAPI {

    private final TurDslToolService turDslToolService;

    public TurSemanticNavToolAPI(TurDslToolService turDslToolService) {
        this.turDslToolService = turDslToolService;
    }

    @Operation(summary = "Test dsl_search tool")
    @GetMapping("/search-site")
    public String searchSite(
            @RequestParam String index,
            @RequestParam(defaultValue = "en") String locale,
            @RequestParam(defaultValue = "{\"query\":{\"match_all\":{}}}") String queryBody) {
        return turDslToolService.search(index, locale, queryBody);
    }

    @Operation(summary = "Test dsl_list_indices tool")
    @GetMapping("/list-sites")
    public String listSites(@RequestParam(defaultValue = "*") String indexPattern) {
        return turDslToolService.listIndices(indexPattern);
    }

    @Operation(summary = "Test dsl_get_mappings tool")
    @GetMapping("/get-site-fields")
    public String getSiteFields(@RequestParam String index) {
        return turDslToolService.getMappings(index);
    }

    @Operation(summary = "Test dsl_get_document tool")
    @GetMapping("/get-document")
    public String getDocument(
            @RequestParam String index,
            @RequestParam(defaultValue = "en") String locale,
            @RequestParam String documentId) {
        return turDslToolService.getDocument(index, locale, documentId);
    }

    @Operation(summary = "Test dsl_suggest tool")
    @GetMapping("/suggest")
    public String suggest(
            @RequestParam String index,
            @RequestParam(defaultValue = "en") String locale,
            @RequestParam String text) {
        return turDslToolService.suggest(index, locale, text);
    }
}
