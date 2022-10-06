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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.wem.beans.TurMultiValue;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.exception.ValidationException;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.logging.context.ContextLogger;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TurSpotlightExtraFields implements ExtAttributeInterface {
    private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData,
                                 IHandlerConfiguration config) throws Exception {
        log.debug("Executing TurSpotlightExtraFields");
        List<TurSpotlightContent> turSpotlightContents = new ArrayList<>();
        Arrays.asList(ci.getRelations(tag.getSrcAttributeRelation().get(0))).forEach(attributedObject -> {
            try {
                Object position = attributedObject.getAttributeValue("POSITION-TUR-SPOTLIGHT-CONTENT");
                Object title = attributedObject.getAttributeValue("TITLE-TUR-SPOTLIGHT-CONTENT");
                Object content = attributedObject.getAttributeValue("CONTENT-TUR-SPOTLIGHT-CONTENT");
                Object link = attributedObject.getAttributeValue("LINK-TUR-SPOTLIGHT-CONTENT");
                String contentText = "";
                if (((String) content).length() == 40 ) {
                    ContentInstance contentInstance = (ContentInstance) ManagedObject
                            .findByContentManagementId(new ManagedObjectVCMRef(content.toString()));
                    TurCTDAttributes turCTDAttributes = new TurCTDAttributes();
                    turCTDAttributes.setId((String) content);
                    turCTDAttributes.setTitle((String) contentInstance.getAttributeValue("title"));
                    contentText = new ObjectMapper().writeValueAsString(turCTDAttributes);
                }
                else {
                    contentText = (String) content;
                }
                TurSpotlightContent turSpotlightContent = new TurSpotlightContent((int) position, (String) title,
                        contentText, (String) link);
                turSpotlightContents.add(turSpotlightContent);
            } catch (ApplicationException | ValidationException | JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
        });
        return TurMultiValue.singleItem(new ObjectMapper().writeValueAsString(turSpotlightContents));
    }

    class TurCTDAttributes {
        private String id;
        private String title;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    class TurSpotlightContent {
        private int position;
        private String title;
        private String content;
        private String link;

        public TurSpotlightContent(int position, String title, String content, String link) {
            super();
            this.position = position;
            this.title = title;
            this.content = content;
            this.link = link;
        }

        public int getPosition() {
            return position;
        }

        public void setPosition(int position) {
            this.position = position;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getLink() {
            return link;
        }

        public void setLink(String link) {
            this.link = link;
        }

    }
}
