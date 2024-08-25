package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.connector.aem.commons.TurAemCommonsUtils;
import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

@Slf4j
public class TurAemExtDeltaDate implements TurAemExtDeltaDateInterface {
    @Override
    public Date consume(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
		log.debug("Executing TurAemExtDeltaDate");
		return TurAemCommonsUtils.getDeltaDate(aemObject);
    }

}
