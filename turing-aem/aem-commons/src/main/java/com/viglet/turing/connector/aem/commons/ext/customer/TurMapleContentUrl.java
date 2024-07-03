package com.viglet.turing.connector.aem.commons.ext.customer;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.ExtAttributeInterface;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurMapleContentUrl implements ExtAttributeInterface {
	@Override
	public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
								 AemObject aemObject, TurAemSourceContext turAemSourceContext) {
		log.debug("Executing TurMapleContentUrl");
		return TurMultiValue
				.singleItem(String.format("%s%s.html", turAemSourceContext.getUrlPrefix(), aemObject
						.getPath().replaceAll("^/content/dam", "/content")));
	}
}
