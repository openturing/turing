package com.viglet.turing.connector.sprinklr.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/api/v2/sprinklr")
@Tag(name = "Heartbeat", description = "Heartbeat")
public class TurSprinklrApi {
    @GetMapping
    public Map<String, String> info() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "ok");
        return status;
    }
}
