package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.aem.indexer.TurAEMIndexerTool;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class TurSiteName implements ExtAttributeInterface {
	private static final String EMPTY_STRING = "";

	@Override
	public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
								 AemObject aemObject, IHandlerConfiguration config, TurAEMIndexerTool turAEMIndexerTool) {
		log.debug("Executing TurSiteName");
		return TurMultiValue.singleItem(EMPTY_STRING);
	}
}
