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

import java.lang.invoke.MethodHandles;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.wem.beans.TurMultiValue;
import com.viglet.turing.connector.wem.beans.TuringTag;
import com.viglet.turing.connector.wem.config.IHandlerConfiguration;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

public class TurHTML2Text implements ExtAttributeInterface {
    private static final ContextLogger logger = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());
    private static final String EMPTY_STRING = "";

    @Override
    public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData,
                                 IHandlerConfiguration config) {
        logger.debug("Executing HTML2Text");
        if (attributeData != null && attributeData.getValue() != null)
            return TurMultiValue.singleItem(TurCommonsUtils.html2Text(attributeData.getValue().toString()));
        else
            return TurMultiValue.singleItem(EMPTY_STRING);

    }
}
