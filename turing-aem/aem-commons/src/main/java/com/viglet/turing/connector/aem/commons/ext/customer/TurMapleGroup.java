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
public class TurMapleGroup implements ExtAttributeInterface {
	public static final String DAM_MODELS = "/conf/maple-bear/settings/dam/cfm/models/";
	public static final String GROUP = "group";

	@Override
	public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
								 AemObject aemObject, TurAemSourceContext turAemSourceContext) {
		log.debug("Executing TurMapleGroup");
		if (aemObject.getModel() != null && aemObject.getModel().equals(DAM_MODELS + "comunicado")
				&& aemObject.getAttributes().containsKey(GROUP)) {
			return TurMultiValue.fromList(((JSONArray) aemObject.getAttributes().get(GROUP)).toList().stream()
					.map(object -> Objects.toString(object, null))
					.toList());
		}
		else {
			return null;
		}
	}
}
