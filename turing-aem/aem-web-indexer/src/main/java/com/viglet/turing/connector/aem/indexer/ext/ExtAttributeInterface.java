package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;

public interface ExtAttributeInterface {
    TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                          AemObject aemObject, TurAemSourceContext turAemSourceContext);
}
