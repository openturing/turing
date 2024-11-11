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
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurAemExtHtml2Text implements TurAemExtAttributeInterface {
    private static final String EMPTY_STRING = "";

    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        log.debug("Executing TurAemExtHtml2Text");
        if (turCmsSourceAttr.getName() != null && aemObject != null && aemObject.getAttributes() != null
                && aemObject.getAttributes().containsKey(turCmsSourceAttr.getName())
                && aemObject.getAttributes().get(turCmsSourceAttr.getName()) != null) {
            return TurMultiValue.singleItem(TurCommonsUtils.html2Text(aemObject.getAttributes()
                    .get(turCmsSourceAttr.getName()).toString()));
        }
        return TurMultiValue.singleItem(EMPTY_STRING);
    }
}