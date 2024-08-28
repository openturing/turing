package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class TurAemExtSourceApps implements TurAemExtAttributeInterface {
	@Override
	public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
								 TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
		log.debug("Executing TurAemExtSourceApps");
		return TurMultiValue.singleItem(turAemSourceContext.getProviderName());
	}
}
