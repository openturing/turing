package com.viglet.turing.api.llm.chat;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.genai.tool.TurSemanticNavToolService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v2/llm/tool")
@Tag(name = "LLM Tool Testing", description = "REST endpoints to test LLM tool services directly")
public class TurSemanticNavToolAPI {

    private final TurSemanticNavToolService turSemanticNavToolService;

    public TurSemanticNavToolAPI(TurSemanticNavToolService turSemanticNavToolService) {
        this.turSemanticNavToolService = turSemanticNavToolService;
    }

    @Operation(summary = "Test search_site tool")
    @GetMapping("/search-site")
    public String searchSite(
            @RequestParam String siteName,
            @RequestParam String locale,
            @RequestParam(defaultValue = "*") String query,
            @RequestParam(defaultValue = "10") int rows,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String filterQueries) {
        return turSemanticNavToolService.searchSite(siteName, locale, query, rows, page, filterQueries);
    }

    @Operation(summary = "Test list_sites tool")
    @GetMapping("/list-sites")
    public String listSites(@RequestParam(defaultValue = "") String filterName) {
        return turSemanticNavToolService.listSites(filterName);
    }

    @Operation(summary = "Test get_site_fields tool")
    @GetMapping("/get-site-fields")
    public String getSiteFields(@RequestParam String siteName) {
        return turSemanticNavToolService.getSiteFields(siteName);
    }
}
