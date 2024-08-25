package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueMap;

public interface TurAemExtContentInterface {
    TurCmsTargetAttrValueMap consume(TurAemObject aemObject, TurAemSourceContext turAemSourceContext);
}
