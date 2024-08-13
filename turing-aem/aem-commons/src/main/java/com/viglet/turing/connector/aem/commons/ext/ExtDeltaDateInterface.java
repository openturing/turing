package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;

import java.util.Date;

public interface ExtDeltaDateInterface {
    Date consume(AemObject aemObject, TurAemSourceContext turAemSourceContext);
}
