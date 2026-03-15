package com.viglet.turing.api.system;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.email.TurWeeklySearchReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/system/email-report")
@Tag(name = "Email Report", description = "Email Report API")
public class TurEmailReportAPI {

    private final TurWeeklySearchReportService weeklySearchReportService;

    public TurEmailReportAPI(TurWeeklySearchReportService weeklySearchReportService) {
        this.weeklySearchReportService = weeklySearchReportService;
    }

    @Operation(summary = "Send weekly search report email immediately")
    @GetMapping("/weekly/send")
    public ResponseEntity<TurEmailReportResponse> sendWeeklyReport() {
        try {
            weeklySearchReportService.sendWeeklyReport();
            return ResponseEntity.ok(new TurEmailReportResponse(true, "Weekly search report sent successfully."));
        } catch (Exception e) {
            log.error("Failed to send weekly search report on demand", e);
            return ResponseEntity.internalServerError()
                    .body(new TurEmailReportResponse(false, "Failed to send report: " + e.getMessage()));
        }
    }

    @Operation(summary = "Preview weekly search report HTML without sending")
    @GetMapping("/weekly/preview")
    public ResponseEntity<String> previewWeeklyReport() {
        try {
            String html = weeklySearchReportService.buildReportHtml();
            return ResponseEntity.ok(html);
        } catch (Exception e) {
            log.error("Failed to build weekly search report preview", e);
            return ResponseEntity.internalServerError().body("Failed to build report: " + e.getMessage());
        }
    }

    public record TurEmailReportResponse(boolean success, String message) {
    }
}
