package com.viglet.turing.connector.webcrawler.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "Heartbeat", description = "Heartbeat")
public class TurWCApi {
    @GetMapping
    public Map<String, String> info() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "ok");
        return status;
    }
}