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

package com.viglet.turing.connector.aem.api;

import com.google.inject.Inject;
import com.viglet.turing.connector.aem.persistence.repository.TurAemIndexingRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/aem")
@Tag(name = "Heartbeat", description = "Heartbeat")
public class TurAemApi {
    private final TurAemIndexingRepository turAemIndexingRepository;

    @Inject
    public TurAemApi(TurAemIndexingRepository turAemIndexingRepository) {
        this.turAemIndexingRepository = turAemIndexingRepository;
    }

    @GetMapping
    public Map<String, String> info() {
        return statusOk();
    }

    @Transactional
    @GetMapping("reindex/{group}")
    public Map<String, String> reindex(@PathVariable String group) {
        turAemIndexingRepository.deleteByIndexGroupAndOnceFalse(group);
        return statusOk();
    }

    @Transactional
    @GetMapping("reindex/once/{group}")
    public Map<String, String> reIndexOnce(@PathVariable String group) {
        turAemIndexingRepository.deleteByIndexGroupAndOnceTrue(group);
        return statusOk();
    }

    @Transactional
    @GetMapping("reindex/{group}/{guid}")
    public Map<String, String> reindexGuid(@PathVariable String group, @PathVariable String guid) {
        turAemIndexingRepository.deleteByAemIdAndIndexGroup(guid, group);
        return statusOk();
    }

    private static Map<String, String> statusOk() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "ok");
        return status;
    }
}
