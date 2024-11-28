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
package com.viglet.turing.connector.wem.ext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.connector.wem.beans.TurMultiValue;
import com.viglet.turing.connector.wem.beans.TuringTag;
import com.viglet.turing.connector.wem.config.IHandlerConfiguration;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.exception.ValidationException;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.logging.context.ContextLogger;
import lombok.Getter;
import lombok.Setter;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TurSpotlightExtraFields implements ExtAttributeInterface {
    private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData,
                                 IHandlerConfiguration config){
        log.debug("Executing TurSpotlightExtraFields");
        List<TurSpotlightContent> turSpotlightContents = new ArrayList<>();
        try {
            Arrays.asList(ci.getRelations(tag.getSrcAttributeRelation().get(0))).forEach(attributedObject -> {
                try {
                    Object position = attributedObject.getAttributeValue("POSITION-TUR-SPOTLIGHT-CONTENT");
                    Object title = attributedObject.getAttributeValue("TITLE-TUR-SPOTLIGHT-CONTENT");
                    Object content = attributedObject.getAttributeValue("CONTENT-TUR-SPOTLIGHT-CONTENT");
                    Object link = attributedObject.getAttributeValue("LINK-TUR-SPOTLIGHT-CONTENT");
                    String contentText;
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
        } catch (ApplicationException | JsonProcessingException e) {
            log.error(e.getMessage(), e);
        }
        return new TurMultiValue();
    }

    @Setter
    @Getter
    static class TurCTDAttributes {
        private String id;
        private String title;

    }

    @Setter
    @Getter
    static class TurSpotlightContent {
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

    }
}
