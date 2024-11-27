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

package com.viglet.turing.api.sn.search;

import com.viglet.turing.commons.se.TurSEFilterQueryParameters;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.sn.TurSNSearchProcess;
import com.viglet.turing.sn.TurSNUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TurSNSiteSearchCachedAPI {
    private final TurSNSearchProcess turSNSearchProcess;

    public TurSNSiteSearchCachedAPI(TurSNSearchProcess turSNSearchProcess) {
        this.turSNSearchProcess = turSNSearchProcess;
    }
    @CacheEvict(value = "searchAPI", allEntries = true)
    @Scheduled(fixedRateString = "${turing.search.cache.ttl.seconds:86400000}")
    public void cleanSearchCache() {
        log.info("Cleaning Search API");
    }

    @Cacheable(value = "searchAPI", key = "#cacheKey")
    public TurSNSiteSearchBean searchCached(String cacheKey,
                                            TurSNSiteSearchContext turSNSiteSearchContext) {
        return turSNSearchProcess.search(turSNSiteSearchContext);
    }
}
