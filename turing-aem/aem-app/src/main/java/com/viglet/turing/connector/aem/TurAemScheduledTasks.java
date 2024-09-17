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

package com.viglet.turing.connector.aem;

import com.google.inject.Inject;
import com.viglet.turing.connector.aem.commons.TurAemCommonsUtils;
import com.viglet.turing.connector.aem.persistence.repository.TurAemSourceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TurAemScheduledTasks {
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
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
            TurAemCommonsUtils.cleanCache();
        });



    }
}
