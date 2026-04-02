package com.viglet.turing.api.system;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.system.TurLlmSummaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v2/summary")
@Tag(name = "Summary", description = "Generic AI-powered summary generation")
@RequiredArgsConstructor
public class TurSummaryAPI {

    private final TurLlmSummaryService llmSummaryService;

    @Operation(summary = "Generate an AI summary from provided data")
    @PostMapping
    public ResponseEntity<TurLlmSummaryService.SummaryResult> generate(
            @RequestBody SummaryRequest request,
            @RequestParam(defaultValue = "false") boolean regenerate) {

        TurLlmSummaryService.SummaryResult result = llmSummaryService.generate(
                request.cacheKey(), request.data(), request.systemPrompt(), regenerate);

        return ResponseEntity.ok(result);
    }

    public record SummaryRequest(String cacheKey, String data, String systemPrompt) {
    }
}
