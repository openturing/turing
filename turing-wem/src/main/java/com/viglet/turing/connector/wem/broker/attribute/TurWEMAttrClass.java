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
package com.viglet.turing.connector.wem.broker.attribute;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.wem.beans.TurAttrDef;
import com.viglet.turing.connector.wem.beans.TurAttrDefContext;
import com.viglet.turing.connector.wem.beans.TurMultiValue;
import com.viglet.turing.connector.wem.beans.TuringTag;
import com.viglet.turing.connector.wem.config.IHandlerConfiguration;
import com.viglet.turing.connector.wem.ext.ExtAttributeInterface;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

public class TurWEMAttrClass {

    private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());
    public static final String HTML = "html";

    private TurWEMAttrClass() {
        throw new IllegalStateException("TurWEMAttrClass");
    }

    public static List<TurAttrDef> attributeByClass(TurAttrDefContext turAttrDefContext, AttributeData attributeData) {

        TuringTag turingTag = turAttrDefContext.getTuringTag();
        ContentInstance ci = turAttrDefContext.getContentInstance();
        IHandlerConfiguration config = turAttrDefContext.getiHandlerConfiguration();
        List<TurAttrDef> attributesDefs = new ArrayList<>();

        if (turingTag.getSrcClassName() != null) {
            String className = turingTag.getSrcClassName();
            if (log.isDebugEnabled())
                log.debug("ClassName : " + className);
            try {
                Object extAttribute = Class.forName(className).getDeclaredConstructor().newInstance();
                TurMultiValue turMultiValue = ((ExtAttributeInterface) extAttribute).consume(turingTag, ci, attributeData,
                        config);
                TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
                attributesDefs.add(turAttrDef);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException | ClassNotFoundException e) {
                log.error(e.getMessage(), e);
            }
        } else {
            TurMultiValue turMultiValue = new TurMultiValue();
            if (turingTag.getSrcAttributeType() != null && turingTag.getSrcAttributeType().equals(HTML)) {
                turMultiValue.add(TurCommonsUtils.html2Text(attributeData.getValue().toString()));
                TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
                attributesDefs.add(turAttrDef);
            } else if (attributeData != null && attributeData.getValue() != null) {
                turMultiValue.add(attributeData.getValue().toString());
                TurAttrDef turAttrDef = new TurAttrDef(turingTag.getTagName(), turMultiValue);
                attributesDefs.add(turAttrDef);
            }

        }
        return attributesDefs;
    }
}
