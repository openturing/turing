package com.viglet.turing.connector.webcrawler.api;

import com.google.inject.Inject;
import com.viglet.turing.connector.webcrawler.export.TurWCExport;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.HashMap;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/api/v2/connector/wc")
@Tag(name = "Heartbeat", description = "Heartbeat")
public class TurWCApi {
    private final TurWCExport turWCExport;

    @Inject
    public TurWCApi(TurWCExport turWCExport) {
        this.turWCExport = turWCExport;
    }
    @GetMapping
    public Map<String, String> info() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "ok");
        return status;
    }
    @GetMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public StreamingResponseBody turSNSiteExport(HttpServletResponse response) {

        try {
            return turWCExport.exportObject(response);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
}
