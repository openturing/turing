package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
@Slf4j
public class TurAEMCreationDate implements ExtAttributeInterface {
	@Override
	public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
								 AemObject aemObject, TurAemSourceContext turAemSourceContext) {
		log.debug("Executing TurAEMCreationDate");
		return Optional.ofNullable(aemObject.getCreatedDate()).map(createdDate ->
				TurMultiValue.singleItem(createdDate.getTime())).orElse(null);
	}

}
