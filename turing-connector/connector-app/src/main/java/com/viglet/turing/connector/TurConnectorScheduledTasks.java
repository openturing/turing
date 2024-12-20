/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.connector;

import com.google.inject.Inject;
import com.viglet.turing.commons.cache.TurCustomClassCache;
import com.viglet.turing.connector.persistence.repository.TurConnectorConfigVarRepository;
import com.viglet.turing.connector.plugin.TurConnectorPlugin;
import jakarta.servlet.ServletContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TurConnectorScheduledTasks {
    private final TurConnectorConfigVarRepository turConnectorConfigVarRepository;
    private final TurConnectorProcess turConnectorProcess;
    private final ServletContext context;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
    public static final String FIRST_TIME = "FIRST_TIME";

    @Inject
    public TurConnectorScheduledTasks(TurConnectorConfigVarRepository turConnectorConfigVarRepository,
                                      TurConnectorProcess turConnectorProcess, ServletContext context) {
        this.turConnectorConfigVarRepository = turConnectorConfigVarRepository;
        this.turConnectorProcess = turConnectorProcess;
        this.context = context;
    }

    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    public void executeWebCrawler() {

        TurCustomClassCache.getCustomClassMap("com.viglet.turing.connector.plugin.webcrawler.TurWCPlugin")
                .ifPresent(classInstance -> {
                    TurConnectorPlugin turConnectorPlugin;
                    turConnectorPlugin = (TurConnectorPlugin) classInstance;
                    ApplicationContext applicationContext = WebApplicationContextUtils.getWebApplicationContext(context);
                    Optional.ofNullable(applicationContext).ifPresent(appContext -> {
                        applicationContext.getAutowireCapableBeanFactory().autowireBean(turConnectorPlugin);
                        turConnectorPlugin.init();
                        log.info("The time is now {}", dateFormat.format(new Date()));
                        if (turConnectorConfigVarRepository.findById(FIRST_TIME).isEmpty()) {
                            log.info("This is the first time, waiting next schedule.");
                        } else {
                            log.info("Starting indexing");
                            turConnectorProcess.start(turConnectorPlugin);
                        }
                    });
                });
    }
}
