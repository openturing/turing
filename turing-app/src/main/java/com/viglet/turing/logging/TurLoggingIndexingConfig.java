package com.viglet.turing.logging;

import com.viglet.turing.commons.logging.TurLoggingIndexingLog;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the configured {@code turing.logging.engine} into the static
 * {@link TurLoggingIndexingLog} so indexing-status events are only emitted when a
 * persistent engine ({@code mongodb} or {@code redis}) is active.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Configuration
public class TurLoggingIndexingConfig {

    private final String loggingEngine;

    public TurLoggingIndexingConfig(@Value("${turing.logging.engine:none}") String loggingEngine) {
        this.loggingEngine = loggingEngine;
    }

    @PostConstruct
    public void configureLoggingEngine() {
        TurLoggingIndexingLog.setEngine(loggingEngine);
    }
}
