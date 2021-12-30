/*
 * Copyright (C) 2016-2019 Alexandre Oliveira <alexandre.oliveira@viglet.com>
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TurSpotlightExtraFields implements ExtAttributeInterface {
    private static final ContextLogger log = ContextLogger.getLogger(TurSpotlightExtraFields.class);

    @Override
    public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData,
                                 IHandlerConfiguration config) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("Executing TurSpotlightExtraFields");
        }
        List<TurSpotlightContent> turSpotlightContents = new ArrayList<>();
        Arrays.asList(ci.getRelations(tag.getSrcAttributeRelation().get(0))).forEach(attributedObject -> {
            try {
                Object position = attributedObject.getAttributeValue("POSITION-TUR-SPOTLIGHT-CONTENT");
                Object title = attributedObject.getAttributeValue("TITLE-TUR-SPOTLIGHT-CONTENT");
                Object content = attributedObject.getAttributeValue("CONTENT-TUR-SPOTLIGHT-CONTENT");
                Object link = attributedObject.getAttributeValue("LINK-TUR-SPOTLIGHT-CONTENT");

                ContentInstance contentInstance = (ContentInstance) ManagedObject
                        .findByContentManagementId(new ManagedObjectVCMRef(content.toString()));
                TurCTDAttributes turCTDAttributes = new TurCTDAttributes();
                turCTDAttributes.setId((String) content);
                turCTDAttributes.setTitle((String) contentInstance.getAttributeValue("title"));
                String ctdJson = new ObjectMapper().writeValueAsString(turCTDAttributes);

                TurSpotlightContent turSpotlightContent = new TurSpotlightContent((int) position, (String) title,
                        ctdJson, (String) link);
                turSpotlightContents.add(turSpotlightContent);
            } catch (ApplicationException | ValidationException | JsonProcessingException e) {
                log.error(e.getMessage(), e);
            }
        });

        String json = new ObjectMapper().writeValueAsString(turSpotlightContents);
        TurMultiValue turMultiValue = new TurMultiValue();
        turMultiValue.add(json);

        return turMultiValue;
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
