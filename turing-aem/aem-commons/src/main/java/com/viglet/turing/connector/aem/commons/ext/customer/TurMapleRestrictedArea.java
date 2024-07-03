package com.viglet.turing.connector.aem.commons.ext.customer;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.ExtAttributeInterface;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;

import java.util.Objects;

@Slf4j
public class TurMapleRestrictedArea implements ExtAttributeInterface {
	@Override
	public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
								 AemObject aemObject, TurAemSourceContext turAemSourceContext) {
		log.debug("Executing TurMapleRestrictedArea");
		if (aemObject.getAttributes().containsKey("contentRestricted") && aemObject.getAttributes()
				.get("contentRestricted").equals(true)
				&& aemObject.getAttributes().containsKey("area")) {
			return TurMultiValue.fromList(((JSONArray) aemObject.getAttributes().get("area")).toList().stream()
					.map(object -> Objects.toString(object, null))
					.toList());
		}
		else {
			return null;
		}
	}
}
