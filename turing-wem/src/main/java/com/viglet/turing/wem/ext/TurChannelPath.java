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

import java.lang.invoke.MethodHandles;

import com.viglet.turing.wem.beans.TurMultiValue;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.common.ref.ChannelRef;
import com.vignette.as.client.javabean.Channel;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

public class TurChannelPath implements ExtAttributeInterface {
    private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData, IHandlerConfiguration config)
            throws Exception {
        log.debug("Executing TurChannelPath");
        StringBuilder channelPath = new StringBuilder();
        ChannelRef[] channelRefs = ci.getChannelAssociations();
        for (int i = 0; i < channelRefs.length; i++) {
            Channel currentChannel = channelRefs[i].getChannel();
            String[] breadcrumb = currentChannel.getBreadcrumbNamePath(true);
            for (int j = 0; j < breadcrumb.length; j++)
                channelPath.append("/" + breadcrumb[j]);
        }
        return TurMultiValue.singleItem(channelPath.toString());
    }

}
