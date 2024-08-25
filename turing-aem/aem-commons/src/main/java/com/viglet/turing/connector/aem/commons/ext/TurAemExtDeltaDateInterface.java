package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;

import java.util.Date;

public interface TurAemExtDeltaDateInterface {
    Date consume(TurAemObject aemObject, TurAemSourceContext turAemSourceContext);
}
