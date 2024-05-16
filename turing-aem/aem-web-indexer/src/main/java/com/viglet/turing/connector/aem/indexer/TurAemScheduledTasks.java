package com.viglet.turing.connector.aem.indexer;

import com.google.inject.Inject;
import com.viglet.turing.connector.aem.indexer.conf.AemHandlerConfiguration;
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
    private final AemHandlerConfiguration aemHandlerConfiguration;

    @Inject
    public TurAemScheduledTasks(TurAemIndexerTool turAemIndexerTool,
                                AemHandlerConfiguration aemHandlerConfiguration) {
        this.turAemIndexerTool = turAemIndexerTool;
        this.aemHandlerConfiguration = aemHandlerConfiguration;
    }

    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    public void executeWebCrawler() {
        log.info("The time is now {}", dateFormat.format(new Date()));
        aemHandlerConfiguration.consume();
        turAemIndexerTool.run();
    }
}