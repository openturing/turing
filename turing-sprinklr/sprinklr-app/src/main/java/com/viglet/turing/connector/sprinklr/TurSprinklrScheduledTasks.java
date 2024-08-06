package com.viglet.turing.connector.sprinklr;

import com.google.inject.Inject;
import com.viglet.turing.connector.sprinklr.persistence.repository.TurSprinklrSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TurSprinklrScheduledTasks {
    private final TurSprinklrProcess turSprinklrProcess;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    private final TurSprinklrSourceRepository turSprinklrSourceRepository;
    @Inject
    public TurSprinklrScheduledTasks(TurSprinklrProcess turSprinklrProcess,
                                     TurSprinklrSourceRepository turSprinklrSourceRepository) {
        this.turSprinklrProcess = turSprinklrProcess;
        this.turSprinklrSourceRepository = turSprinklrSourceRepository;
    }

    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    public void executeSprinklrCrawler() {
        log.info("The time is now {}", dateFormat.format(new Date()));
        turSprinklrSourceRepository.findAll().forEach(turSprinklrProcess::start);

    }
}
