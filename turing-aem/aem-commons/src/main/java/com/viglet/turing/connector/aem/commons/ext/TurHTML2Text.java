/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurHTML2Text implements ExtAttributeInterface {
    private static final String EMPTY_STRING = "";

    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 AemObject aemObject, TurAemSourceContext turAemSourceContext) {
        log.debug("Executing HTML2Text");
        if (turCmsSourceAttr.getName() != null && aemObject != null && aemObject.getAttributes() != null
                && aemObject.getAttributes().containsKey(turCmsSourceAttr.getName())
                && aemObject.getAttributes().get(turCmsSourceAttr.getName()) != null) {
            return TurMultiValue.singleItem(TurCommonsUtils.html2Text(aemObject.getAttributes()
                    .get(turCmsSourceAttr.getName()).toString()));
        }
        return TurMultiValue.singleItem(EMPTY_STRING);
    }
}
