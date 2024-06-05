package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.aem.indexer.TurAemContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;

public interface ExtAttributeInterface {
    TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                          AemObject aemObject, IHandlerConfiguration config, TurAemContext turAemContext);
}