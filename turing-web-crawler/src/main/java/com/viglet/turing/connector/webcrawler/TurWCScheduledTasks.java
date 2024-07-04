package com.viglet.turing.connector.webcrawler;

import com.google.inject.Inject;
import com.viglet.turing.connector.webcrawler.persistence.repository.TurWCSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TurWCScheduledTasks {
    private final TurWCSourceRepository turWCSourceRepository;
    private final TurWCProcess turWCProcess;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    @Inject
    public TurWCScheduledTasks(TurWCSourceRepository turWCSourceRepository, TurWCProcess turWCProcess) {
        this.turWCSourceRepository = turWCSourceRepository;
        this.turWCProcess = turWCProcess;
    }

    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    public void executeWebCrawler() {
        log.info("The time is now {}", dateFormat.format(new Date()));
        turWCSourceRepository.findAll().forEach(turWCProcess::start);
    }
}
