package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.TurAEMCommonsUtils;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Optional;

@Slf4j
public class TurAEMDeltaDate implements ExtDeltaDateInterface {
    @Override
    public Date consume(AemObject aemObject, TurAemSourceContext turAemSourceContext) {
		log.debug("Executing TurAEMDeltaDate");
		return TurAEMCommonsUtils.getDeltaDate(aemObject);
    }
}
