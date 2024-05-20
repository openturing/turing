/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.api.sn.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.filesystem.commons.TurFileAttributes;
import com.viglet.turing.filesystem.commons.TurFileUtils;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.TurSNConstants;
import com.viglet.turing.utils.TurUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/sn/import")
@Tag(name = "Semantic Navigation Import", description = "Semantic Navigation Import API")
public class TurSNImportAPI {
    private final JmsMessagingTemplate jmsMessagingTemplate;
    private final TurSNSiteRepository turSNSiteRepository;

    @Inject
    public TurSNImportAPI(JmsMessagingTemplate jmsMessagingTemplate, TurSNSiteRepository turSNSiteRepository) {
        this.jmsMessagingTemplate = jmsMessagingTemplate;
        this.turSNSiteRepository = turSNSiteRepository;
    }

    @PostMapping
    public boolean turSNImportBroker(@RequestBody TurSNJobItems turSNJobItems) {
      send(turSNJobItems);
      return true;
    }

    private void importUnsuccessful(String siteName, TurSNJobItems turSNJobItems) {
        turSNJobItems.forEach(turSNJobItem -> {
            if (turSNJobItem != null) {
                if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.CREATE)) {
                    log.warn(
                            "Create Object ID '{}' of '{}' SN Site ({}) was not processed. Because '{}' SN Site doesn't exist",
                            turSNJobItem.getAttributes() != null
                                    ? turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE)
                                    : null,
                            siteName, turSNJobItem.getLocale(), siteName);
                } else if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.DELETE)) {
                    if (turSNJobItem.getAttributes() != null
                            && turSNJobItem.getAttributes().containsKey(TurSNConstants.ID_ATTRIBUTE)) {
                        log.warn(
                                "Delete Object ID '{}' of '{}' SN Site ({}) was not processed. Because '{}' SN Site doesn't exist",
                                turSNJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE), siteName,
                                turSNJobItem.getLocale(), siteName);
                    } else {
                        log.warn(
                                "Delete Object ID '{}' of '{}' SN Site ({}) was not processed. Because '{}' SN Site doesn't exist",
                                turSNJobItem.getAttributes() != null ?
                                        turSNJobItem.getAttributes().get(TurSNConstants.TYPE_ATTRIBUTE) : "empty", siteName,
                                turSNJobItem.getLocale(), siteName);
                    }
                }
            } else {
                log.warn("No JobItem' of '{}' SN Site", siteName);
            }
        });
    }

    @PostMapping("zip")
    public boolean turSNImportZipFileBroker(@RequestParam("file") MultipartFile multipartFile) {
        File extractFolder = TurUtils.extractZipFile(multipartFile);
        try (FileInputStream fileInputStream = new FileInputStream(
                extractFolder.getAbsolutePath().concat(File.separator).concat(TurSNConstants.EXPORT_FILE))) {
            TurSNJobItems turSNJobItems = new ObjectMapper().readValue(fileInputStream, TurSNJobItems.class);
            turSNJobItems.forEach(turSNJobItem -> turSNJobItem.getAttributes().entrySet()
                    .forEach(attribute -> extractTextOfFileAttribute(extractFolder, attribute)));
            try {
                FileUtils.deleteDirectory(extractFolder);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            return turSNImportBroker(turSNJobItems);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    private void extractTextOfFileAttribute(File extractFolder, Map.Entry<String, Object> attribute) {
        if (attribute.getValue().toString().startsWith(TurSNConstants.FILE_PROTOCOL)) {
            String fileName = attribute.getValue().toString().replace(TurSNConstants.FILE_PROTOCOL, "");
            try (FileInputStream fileInputStreamAttribute = new FileInputStream(
                    extractFolder.getAbsolutePath() + File.separator + fileName)) {
                TurFileAttributes turFileAttributes = TurFileUtils.parseFile(fileInputStreamAttribute, null);
                Optional.ofNullable(turFileAttributes)
                        .map(TurFileAttributes::getContent)
                        .ifPresent(content -> attribute.setValue(TurCommonsUtils.cleanTextContent(content)));
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }

        }
    }

    public void send(TurSNJobItems turSNJobItems) {
        sentQueueInfo(turSNJobItems);
        if (log.isDebugEnabled()) {
            log.debug("Sent job - {}", TurSNConstants.INDEXING_QUEUE);
            log.debug("turSNJob: {}", turSNJobItems);
        }
        this.jmsMessagingTemplate.convertAndSend(TurSNConstants.INDEXING_QUEUE, turSNJobItems);
    }

    private void sentQueueInfo(TurSNJobItems turSNJobItems) {
        turSNJobItems.forEach(turJobItem -> {
            if (isValidJobItem(turJobItem)) {
                turJobItem.getSiteNames().forEach(siteName ->
                        turSNSiteRepository.findByName(siteName).ifPresentOrElse(turSNSite ->
                                log.info("Sent to queue to {} the Object ID '{}' of '{}' SN Site ({}).",
                                        actionType(turJobItem),
                                        turJobItem.getAttributes().get(TurSNConstants.ID_ATTRIBUTE),
                                        turSNSite.getName(),
                                        turJobItem.getLocale()),
                                () -> importUnsuccessful(siteName, turSNJobItems)));
            }
        });
    }

    private static boolean isValidJobItem(TurSNJobItem turJobItem) {
        return turJobItem != null && turJobItem.getAttributes() != null &&
                turJobItem.getAttributes().containsKey(TurSNConstants.ID_ATTRIBUTE);
    }

    @NotNull
    private static String actionType(TurSNJobItem turJobItem) {
        return switch (turJobItem.getTurSNJobAction()) {
            case CREATE -> "index";
            case DELETE -> "deIndex";
        };
    }
}
