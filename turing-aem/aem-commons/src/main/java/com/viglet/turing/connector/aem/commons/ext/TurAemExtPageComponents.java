/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.TurAemCommonsUtils;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.aem.commons.mappers.TurAemSourceAttr;
import com.viglet.turing.connector.aem.commons.mappers.TurAemTargetAttr;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

@Slf4j
public class TurAemExtPageComponents implements TurAemExtAttributeInterface {

	public static final String RESPONSIVE_GRID = "responsivegrid";
	public static final String ROOT = "root";

	@Override
	public TurMultiValue consume(TurAemTargetAttr turAemTargetAttr, TurAemSourceAttr turAemSourceAttr,
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
