package com.viglet.turing.genai.tool;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurDateTimeToolService {

    @Tool(name = "get_current_time", description = ".")
    public String getCurrentTime(String timezone) {
        log.info("[DateTime Tool] get_current_time called: timezone={}", timezone);
        try {
            ZoneId zoneId = ZoneId.of(timezone);
            ZonedDateTime now = ZonedDateTime.now(zoneId);

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                    "EEEE, MMMM d, yyyy 'at' HH:mm:ss z (O)", Locale.ENGLISH);

            String result = "Current date and time in " + zoneId.getId() + ": " + now.format(formatter);
            log.info("[DateTime Tool] get_current_time: {}", result);
            return result;
        } catch (Exception e) {
            log.error("[DateTime Tool] get_current_time failed for {}: {}", timezone, e.getMessage(), e);
            return "Error: invalid timezone '" + timezone
                    + "'. Use IANA format like 'America/New_York', 'Europe/London', 'Asia/Tokyo', or 'UTC'.";
        }
    }
}
