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

import com.viglet.turing.connector.wem.broker.indexer.TurWEMDeindex;
import com.viglet.turing.connector.wem.config.IHandlerConfiguration;
import com.viglet.turing.connector.wem.index.IValidToIndex;
import com.viglet.turing.connector.wem.util.TuringUtils;
import com.vignette.as.client.common.WhereClause;
import com.vignette.as.client.common.ref.ChannelRef;
import com.vignette.as.client.javabean.Channel;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;

public class TurDeindexParentChannel implements IValidToIndex {
	@Override
	public boolean isValid(ContentInstance ci, IHandlerConfiguration config) throws Exception {

		if (log.isDebugEnabled()) {
			log.debug("Executing TurDeindexParentChannel");
		}
		ChannelRef[] channelRefs = ci.getChannelAssociations();
		if (channelRefs.length > 0) {
			String siteName = TuringUtils.getSiteNameFromContentInstance(ci, config);
			for (ChannelRef channelRef : channelRefs) {
				Channel parentChannel = TuringUtils
						.getParentChannelFromBreadcrumb(channelRef.getChannel().getBreadcrumbPath(true));
				for (ManagedObject vgnExtPage : TuringUtils.getVgnExtPagesFromChannel(parentChannel)) {
					TurWEMDeindex.indexDelete(vgnExtPage.getContentManagementId(), config, siteName);
				}
			}
		}
		return true;
	}

	@Override
	public void whereToValid(WhereClause clause, IHandlerConfiguration config) throws Exception {
		// Do nothing
	}

}
