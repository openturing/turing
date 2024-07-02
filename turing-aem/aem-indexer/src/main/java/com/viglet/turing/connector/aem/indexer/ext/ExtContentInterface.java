package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.indexer.TurAEMIndexerTool;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueList;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;

public interface ExtContentInterface {
	TurCmsTargetAttrValueList consume(AemObject aemObject, IHandlerConfiguration config, TurAEMIndexerTool turAEMIndexerTool);
}
