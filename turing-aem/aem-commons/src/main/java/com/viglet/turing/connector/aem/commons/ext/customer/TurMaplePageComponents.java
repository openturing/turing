package com.viglet.turing.connector.aem.commons.ext.customer;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.TurAEMCommonsUtils;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.TurPageComponents;
import com.viglet.turing.connector.aem.commons.ext.ExtAttributeInterface;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@Slf4j
public class TurMaplePageComponents implements ExtAttributeInterface {
	@Override
	public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
								 AemObject aemObject, TurAemSourceContext turAemSourceContext) {
		log.debug("Executing TurMaplePageComponents");
		String pageURL = aemObject.getPath().replaceAll("^/content/dam", "/content");
		final JSONObject jsonObject = TurAEMCommonsUtils.getInfinityJson(pageURL, turAemSourceContext);
		return TurPageComponents.getResponsiveGridContent(new AemObject(pageURL, jsonObject));
	}
}
