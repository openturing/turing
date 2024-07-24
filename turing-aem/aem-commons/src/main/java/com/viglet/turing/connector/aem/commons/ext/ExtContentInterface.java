package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueMap;

public interface ExtContentInterface {
    TurCmsTargetAttrValueMap consume(AemObject aemObject, TurAemSourceContext turAemSourceContext);
}
