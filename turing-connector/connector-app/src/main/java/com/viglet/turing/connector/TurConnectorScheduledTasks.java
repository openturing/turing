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
import com.viglet.turing.connector.commons.plugin.TurConnectorPlugin;
import com.viglet.turing.connector.impl.TurConnectorContextImpl;
import com.viglet.turing.connector.persistence.repository.TurConnectorConfigVarRepository;
import jakarta.servlet.ServletContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TurConnectorScheduledTasks {
    private final String pluginClassName;
    private final TurConnectorContextImpl turConnectorContext;
    private final TurConnectorConfigVarRepository turConnectorConfigVarRepository;
    private final ServletContext servletContext;
    public static final String FIRST_TIME = "FIRST_TIME";

    @Inject
    public TurConnectorScheduledTasks(@Value("${turing.connector.plugin}") String pluginClassName,
                                      TurConnectorContextImpl turConnectorContext,
                                      TurConnectorConfigVarRepository turConnectorConfigVarRepository,
                                      ServletContext servletContext) {
        this.turConnectorContext = turConnectorContext;
        this.turConnectorConfigVarRepository = turConnectorConfigVarRepository;
        this.servletContext = servletContext;
        this.pluginClassName = pluginClassName;
    }

    @Scheduled(fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    public void executeWebCrawler() {
        TurCustomClassCache.getCustomClassMap(pluginClassName)
                .ifPresent(classInstance -> {
                    TurConnectorPlugin turConnectorPlugin;
                    turConnectorPlugin = (TurConnectorPlugin) classInstance;
                    ApplicationContext applicationContext = WebApplicationContextUtils
                            .getWebApplicationContext(servletContext);
                    Optional.ofNullable(applicationContext).ifPresent(appContext -> {
                        if (turConnectorConfigVarRepository.findById(FIRST_TIME).isEmpty()) {
                            log.info("This is the first time, waiting next schedule.");
                        } else {
                            log.info("Starting indexing");
                            applicationContext.getAutowireCapableBeanFactory().autowireBean(turConnectorPlugin);
                            turConnectorPlugin.init(turConnectorContext);
                        }
                    });
                });
    }
}
