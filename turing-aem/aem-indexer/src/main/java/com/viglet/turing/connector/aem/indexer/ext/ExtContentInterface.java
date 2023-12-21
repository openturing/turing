package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.aem.indexer.TurAEMIndexerTool;
import com.viglet.turing.connector.cms.beans.TurAttrDef;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;

import java.util.List;

public interface ExtContentInterface {
	List<TurAttrDef> consume(AemObject aemObject, IHandlerConfiguration config, TurAEMIndexerTool turAEMIndexerTool);
}