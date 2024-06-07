package com.viglet.turing.connector.aem.indexer;

import com.google.inject.Inject;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TurAemScheduledTasks {
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    private final TurAemIndexerTool turAemIndexerTool;
    private final TurAemSourceRepository turAemSourceRepository;
    @Inject
    public TurAemScheduledTasks(TurAemIndexerTool turAemIndexerTool,
                                TurAemSourceRepository turAemSourceRepository
                               ) {
        this.turAemIndexerTool = turAemIndexerTool;
        this.turAemSourceRepository = turAemSourceRepository;
    }

    @Scheduled(fixedDelayString = "${turing.aem.job.interval.minutes:5}", timeUnit = TimeUnit.MINUTES)
    public void executeWebCrawler() {
        log.info("The time is now {}", dateFormat.format(new Date()));
        turAemSourceRepository.findAll().forEach(turAemSource -> {
            turAemIndexerTool.run(turAemSource);
            TurAemUtils.cleanCache();
        });



    }
}