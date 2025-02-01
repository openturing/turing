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
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.filesystem.commons.TurFileUtils;
import com.viglet.turing.connector.filesystem.commons.TurTikaFileAttributes;
import com.viglet.turing.genai.TurGenAI;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.sn.TurSNConstants;
import com.viglet.turing.spring.utils.TurSpringUtils;
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
    private final TurGenAI turLlm;
    @Inject
    public TurSNImportAPI(JmsMessagingTemplate jmsMessagingTemplate, TurSNSiteRepository turSNSiteRepository,
                          TurGenAI turLlm) {
        this.jmsMessagingTemplate = jmsMessagingTemplate;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turLlm = turLlm;
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
                    log.error(
                            "Create Object ID '{}' of '{}' SN Site ({}) was not processed. Because '{}' SN Site doesn't exist",
                            turSNJobItem.getAttributes() != null
                                    ? turSNJobItem.getAttributes().get(TurSNFieldName.ID)
                                    : null,
                            siteName, turSNJobItem.getLocale(), siteName);
                } else if (turSNJobItem.getTurSNJobAction().equals(TurSNJobAction.DELETE)) {
                    log.error(
                            "Delete Object ID '{}' of '{}' SN Site ({}) was not processed. Because '{}' SN Site doesn't exist",
                            turSNJobItem.getAttributes() != null ?
                                    turSNJobItem.getAttributes().get(TurSNFieldName.TYPE) : "empty", siteName,
                            turSNJobItem.getLocale(), siteName);
                }
            } else {
                log.error("No JobItem' of '{}' SN Site", siteName);
            }
        });
    }

    @PostMapping("zip")
    public boolean turSNImportZipFileBroker(@RequestParam("file") MultipartFile multipartFile) {
        File extractFolder = TurSpringUtils.extractZipFile(multipartFile);
        try (FileInputStream fileInputStream = new FileInputStream(
                extractFolder.getAbsolutePath().concat(File.separator).concat(TurSNConstants.EXPORT_FILE))) {
            TurSNJobItems turSNJobItems = new ObjectMapper().readValue(fileInputStream, TurSNJobItems.class);
            turSNJobItems.forEach(turSNJobItem -> turSNJobItem.getAttributes().entrySet()
                    .forEach(attribute -> extractTextOfFileAttribute(extractFolder, attribute)));
            FileUtils.deleteDirectory(extractFolder);
            return turSNImportBroker(turSNJobItems);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    private void extractTextOfFileAttribute(File extractFolder, Map.Entry<String, Object> attribute) {
        if (attribute.getValue().toString().startsWith(TurSNConstants.FILE_PROTOCOL)) {
            String fileName = attribute.getValue().toString().replace(TurSNConstants.FILE_PROTOCOL, "");
            File file = new File(extractFolder.getAbsolutePath().concat(File.separator).concat(fileName));
            TurTikaFileAttributes turTikaFileAttributes = TurFileUtils.parseFile(file);
            Optional.ofNullable(turTikaFileAttributes)
                    .map(TurTikaFileAttributes::getContent)
                    .ifPresent(content -> attribute.setValue(TurCommonsUtils.cleanTextContent(content)));
        }
    }

    public void send(TurSNJobItems turSNJobItems) {
        sendToLlm(turSNJobItems);
        sentQueueInfo(turSNJobItems);
        if (log.isDebugEnabled()) {
            log.debug("Sent job - {}", TurSNConstants.INDEXING_QUEUE);
            log.debug("turSNJob: {}", turSNJobItems);
        }
        this.jmsMessagingTemplate.convertAndSend(TurSNConstants.INDEXING_QUEUE, turSNJobItems);
    }

    private void sendToLlm(TurSNJobItems turSNJobItems) {
        turLlm.addDocuments(turSNJobItems);
    }

    private void sentQueueInfo(TurSNJobItems turSNJobItems) {
        turSNJobItems.forEach(turJobItem -> {
            if (isValidJobItem(turJobItem)) {
                turJobItem.getSiteNames().forEach(siteName ->
                        turSNSiteRepository.findByName(siteName).ifPresentOrElse(turSNSite ->
                                        log.info("Sent to queue to {} the Object ID '{}' of '{}' SN Site ({}).",
                                                actionType(turJobItem),
                                                turJobItem.getAttributes().get(TurSNFieldName.ID),
                                                turSNSite.getName(),
                                                turJobItem.getLocale()),
                                () -> importUnsuccessful(siteName, turSNJobItems)));
            }
        });
    }

    private static boolean isValidJobItem(TurSNJobItem turJobItem) {
        return turJobItem != null && turJobItem.getAttributes() != null &&
                turJobItem.getAttributes().containsKey(TurSNFieldName.ID);
    }

    @NotNull
    private static String actionType(TurSNJobItem turJobItem) {
        return switch (turJobItem.getTurSNJobAction()) {
            case CREATE -> "index";
            case DELETE -> "deIndex";
        };
    }
}
