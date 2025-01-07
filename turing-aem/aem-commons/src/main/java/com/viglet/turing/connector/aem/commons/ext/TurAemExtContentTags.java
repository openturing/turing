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

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.aem.commons.TurAemCommonsUtils;
import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.bean.TurAemContentTag;
import com.viglet.turing.connector.aem.commons.bean.TurAemContentTags;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.mappers.TurAemSourceAttr;
import com.viglet.turing.connector.aem.commons.mappers.TurAemTargetAttr;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class TurAemExtContentTags implements TurAemExtAttributeInterface {
    public static final String TAGS_JSON_EXTENSION = "/jcr:content.tags.json";
    private static final TurMultiValue EMPTY = null;

    @Override
    public TurMultiValue consume(TurAemTargetAttr turAemTargetAttr, TurAemSourceAttr turAemSourceAttr,
                                 TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        log.debug("Executing TurAemExtContentTags");
        return new TurMultiValue(getTags(aemObject, turAemSourceContext).map(tags ->
                        tags.getTags().stream()
                                .map(TurAemContentTag::getTagID)
                                .toList())
                .orElse(EMPTY));
    }

    public static Optional<TurAemContentTags> getTags(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        String url = turAemSourceContext.getUrl() + aemObject.getPath() + TAGS_JSON_EXTENSION;
        return TurAemCommonsUtils.getResponseBody(url, turAemSourceContext, TurAemContentTags.class, true);
    }
}
