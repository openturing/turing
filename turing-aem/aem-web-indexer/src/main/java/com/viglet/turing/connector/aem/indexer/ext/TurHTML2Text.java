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
package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.aem.indexer.TurAemContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;

@Slf4j
public class TurHTML2Text implements ExtAttributeInterface {
    private static final String EMPTY_STRING = "";

    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 AemObject aemObject, TurAemContext turAemContext) {
        log.debug("Executing HTML2Text");
        if (turCmsSourceAttr.getName() != null && aemObject != null && aemObject.getAttributes() != null
                && aemObject.getAttributes().containsKey(turCmsSourceAttr.getName())
                && aemObject.getAttributes().get(turCmsSourceAttr.getName()) != null) {
            return TurMultiValue.singleItem(Jsoup.parse(aemObject.getAttributes()
                    .get(turCmsSourceAttr.getName()).toString()).text());
        }
        return TurMultiValue.singleItem(EMPTY_STRING);
    }
}
