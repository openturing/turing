package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.TurAEMCommonsUtils;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

@Slf4j
public class TurAEMDeltaDate implements ExtDeltaDateInterface {
    @Override
    public Date consume(AemObject aemObject, TurAemSourceContext turAemSourceContext) {
		log.debug("Executing TurAEMDeltaDate");
		return TurAEMCommonsUtils.getDeltaDate(aemObject);
    }
}
