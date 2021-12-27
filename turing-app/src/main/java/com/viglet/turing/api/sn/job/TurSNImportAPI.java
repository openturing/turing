/*
 * Copyright (C) 2016-2019 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.api.sn.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.utils.TurUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@RestController
@RequestMapping("/api/sn/{siteName}/import")
@Tag(name = "Semantic Navigation Import", description = "Semantic Navigation Import API")
public class TurSNImportAPI {
    private static final Logger logger = LogManager.getLogger(TurSNImportAPI.class.getName());
    @Autowired
    private JmsMessagingTemplate jmsMessagingTemplate;
    @Autowired
    private TurSNSiteRepository turSNSiteRepository;
    private static final String EXPORT_FILE = "export.json";
    public static final String INDEXING_QUEUE = "indexing.queue";

    @PostMapping
    public boolean turSNImportBroker(@PathVariable String siteName, @RequestBody TurSNJobItems turSNJobItems) {
        TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);
        if (turSNSite != null) {
            TurSNJob turSNJob = new TurSNJob();
            turSNJob.setSiteId(turSNSite.getId());
            turSNJob.setTurSNJobItems(turSNJobItems);
            send(turSNJob);
            return true;
        } else {
            return false;
        }

    }

    @PostMapping("zip")
    public boolean turSNImportZipFileBroker(@PathVariable String siteName, @RequestParam("file") MultipartFile multipartFile) {
        File extractFolder = TurUtils.extractZipFile(multipartFile);
        if (extractFolder != null) {
            try {
                TurSNJobItems turSNJobItems = new ObjectMapper().readValue(
                        new FileInputStream(extractFolder.getAbsolutePath().concat(File.separator).concat(EXPORT_FILE)),
                        TurSNJobItems.class);
                return turSNImportBroker(siteName, turSNJobItems);
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return false;
    }

    public void send(TurSNJob turSNJob) {

        sentQueueInfo(turSNJob);

        if (logger.isDebugEnabled()) {
            logger.debug("Sent job - {}", INDEXING_QUEUE);
            logger.debug("turSNJob: {}", turSNJob.getTurSNJobItems());
        }
        this.jmsMessagingTemplate.convertAndSend(INDEXING_QUEUE, turSNJob);

    }

    private void sentQueueInfo(TurSNJob turSNJob) {
        TurSNSite turSNSite = turSNSiteRepository.findById(turSNJob.getSiteId()).orElse(null);
        turSNJob.getTurSNJobItems().forEach(turJobItem -> {
            if (turSNSite != null && turJobItem != null && turJobItem.getAttributes() != null
                    && turJobItem.getAttributes().containsKey("id")) {
                String action = null;
                if (turJobItem.getTurSNJobAction().equals(TurSNJobAction.CREATE)) {
                    action = "index";
                } else if (turJobItem.getTurSNJobAction().equals(TurSNJobAction.DELETE)) {
                    action = "deindex";
                }
                logger.info("Sent to queue to {} the Object ID '{}' of '{}' SN Site.", action,
                        turJobItem.getAttributes().get("id"), turSNSite.getName());

            }
        });
    }
}
