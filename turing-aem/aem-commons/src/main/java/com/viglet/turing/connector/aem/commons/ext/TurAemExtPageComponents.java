package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.TurAemCommonsUtils;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

@Slf4j
public class TurAemExtPageComponents implements TurAemExtAttributeInterface {

	public static final String RESPONSIVE_GRID = "responsivegrid";
	public static final String ROOT = "root";

	@Override
	public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
								 TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
		log.debug("Executing TurAemExtPageComponents");
		return getResponsiveGridContent(aemObject);
	}

	@NotNull
	public static TurMultiValue getResponsiveGridContent(TurAemObject aemObject) {
		StringBuilder components = new StringBuilder();
		if(aemObject.getJcrContentNode() != null && aemObject.getJcrContentNode().has(ROOT)
				&& aemObject.getJcrContentNode().get(ROOT) instanceof JSONObject root
				&& root.has(RESPONSIVE_GRID)
				&& root.get(RESPONSIVE_GRID) instanceof JSONObject responsiveGrid) {
			TurAemCommonsUtils.getJsonNodeToComponent(responsiveGrid, components);
		}
		return TurMultiValue.singleItem(TurCommonsUtils.html2Text(components.toString()));
	}
}
