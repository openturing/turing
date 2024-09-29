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
import com.viglet.turing.connector.wem.util.ETLTuringTranslator;
import com.viglet.turing.connector.wem.util.TuringUtils;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.common.ref.ChannelRef;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.exception.AuthorizationException;
import com.vignette.as.client.exception.ValidationException;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

import java.lang.invoke.MethodHandles;
import java.rmi.RemoteException;

public class TurParentChannel implements ExtAttributeInterface {
    private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());

    public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData,
                                 IHandlerConfiguration config) {
        log.debug("Executing TurParentChannel");
        try {
            ChannelRef[] channelRefs = ci.getChannelAssociations();

            if (channelRefs.length > 0) {
                ETLTuringTranslator etlTranslator = new ETLTuringTranslator(config);
                String channelId = TuringUtils
                        .getParentChannelFromBreadcrumb(channelRefs[0].getChannel().getBreadcrumbPath(true))
                        .getContentManagementId().getId();
                return TurMultiValue.singleItem(etlTranslator.translateByGUID(channelId));
            }
        } catch (ApplicationException | AuthorizationException | ValidationException | RemoteException e) {
            log.error(e.getMessage(), e);
        }
        return new TurMultiValue();
    }
}
