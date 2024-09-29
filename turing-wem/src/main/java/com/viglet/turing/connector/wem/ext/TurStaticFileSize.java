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

import com.viglet.turing.connector.wem.beans.TurMultiValue;
import com.viglet.turing.connector.wem.beans.TuringTag;
import com.viglet.turing.connector.wem.config.IHandlerConfiguration;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.exception.ValidationException;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.as.client.javabean.StaticFile;
import com.vignette.logging.context.ContextLogger;

import java.io.File;
import java.lang.invoke.MethodHandles;


public class TurStaticFileSize implements ExtAttributeInterface {
    private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());
    private static final String EMPTY_STRING = "";

    @Override
    public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData,
                                 IHandlerConfiguration config) {
        log.debug("Executing TurStaticFileSize");
        if (attributeData != null && attributeData.getValue() != null) {
            try {
                StaticFile staticFile = (StaticFile) ManagedObject
                        .findByContentManagementId(new ManagedObjectVCMRef(attributeData.getValue().toString()));


                File file = new File(config.getFileSourcePath().concat(staticFile.getPlacementPath()));
                log.info(file.getAbsolutePath());
                return TurMultiValue.singleItem(file.exists() ? Long.toString(file.length()) : "0");
            } catch (ApplicationException | ValidationException e) {
                log.error(e.getMessage(), e);
            }
        }
        return TurMultiValue.singleItem(EMPTY_STRING);

    }
}
