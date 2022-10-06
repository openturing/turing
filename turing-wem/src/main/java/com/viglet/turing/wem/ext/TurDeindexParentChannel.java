package com.viglet.turing.wem.ext;

import com.viglet.turing.wem.broker.indexer.TurWEMDeindex;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.index.IValidToIndex;
import com.viglet.turing.wem.util.TuringUtils;
import com.vignette.as.client.common.WhereClause;
import com.vignette.as.client.common.ref.ChannelRef;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.javabean.Channel;
import com.vignette.as.client.javabean.ContentInstance;

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
				ManagedObjectVCMRef parentChannel = getParentChannelFromBreadcrumb(
						channelRef.getChannel().getBreadcrumbPath(true));
				TurWEMDeindex.indexDelete(parentChannel, config, siteName);
			}
		}
		return true;
	}

	private ManagedObjectVCMRef getParentChannelFromBreadcrumb(Channel[] breadcrumb) {
		return breadcrumb[breadcrumb.length - 1].getContentManagementId();
	}

	@Override
	public void whereToValid(WhereClause clause, IHandlerConfiguration config) throws Exception {
	}

}
