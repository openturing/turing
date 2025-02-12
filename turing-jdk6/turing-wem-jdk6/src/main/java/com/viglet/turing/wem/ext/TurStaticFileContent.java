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
package com.viglet.turing.wem.ext;

import com.viglet.turing.client.ocr.TurFileAttributes;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.credentials.TurUsernamePasswordCredentials;
import com.viglet.turing.wem.beans.TurMultiValue;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.as.client.javabean.StaticFile;
import com.vignette.logging.context.ContextLogger;

import java.io.File;
import java.net.URL;

public class TurStaticFileContent implements ExtAttributeInterface {
    private static final ContextLogger logger = ContextLogger.getLogger(TurStaticFileContent.class.getName());
    private static final String EMPTY_STRING = "";
    private static final long MAX_CONTENT_MEGA_BYTE_SIZE = 5;
    private static final long MEGA_BYTE = 1024L * 1024L;

    @Override
    public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData,
                                 IHandlerConfiguration config) throws Exception {
        logger.debug("Executing TurStaticFileContent");
        if (attributeData != null && attributeData.getValue() != null) {
            StaticFile staticFile = (StaticFile) ManagedObject
                    .findByContentManagementId(new ManagedObjectVCMRef(attributeData.getValue().toString()));
            if (staticFile != null && staticFile.getPlacementPath() != null) {
                File file = new File(config.getFileSourcePath().concat(staticFile.getPlacementPath()));
                if (file.exists() && file.isFile() && getFileSizeMegaBytes(file) <= MAX_CONTENT_MEGA_BYTE_SIZE) {
                    TurSNServer turSNServer = new TurSNServer(new URL(config.getTuringURL()),
                            new TurUsernamePasswordCredentials(config.getLogin(), config.getPassword()));
                    TurOcr turOcr = new TurOcr();
                    TurFileAttributes turFileAttributes = turOcr.processFile(turSNServer, file);
                    if (turFileAttributes.getContent() != null) {
                        long maxContentByteSize = MAX_CONTENT_MEGA_BYTE_SIZE * MEGA_BYTE;

                        if (turFileAttributes.getContent().getBytes().length <= maxContentByteSize) {
                            return TurMultiValue.singleItem(turFileAttributes.getContent());
                        } else {
                            return TurMultiValue.singleItem(
                                    turFileAttributes.getContent().substring(0, (int) maxContentByteSize));
                        }
                    }
                }
            }
        }
        return TurMultiValue.singleItem(EMPTY_STRING);
    }

    private static long getFileSizeMegaBytes(File file) {
        return (long) ((double) file.length() / MEGA_BYTE);
    }

}
