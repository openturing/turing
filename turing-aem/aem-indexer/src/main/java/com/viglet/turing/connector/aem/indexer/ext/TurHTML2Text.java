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
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.util.HtmlManipulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.lang.invoke.MethodHandles;

public class TurHTML2Text implements ExtAttributeInterface {
	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String EMPTY_STRING = "";

    @Override
    public TurMultiValue consume(TuringTag tag, AemObject aemObject, IHandlerConfiguration config) {
        logger.debug("Executing HTML2Text");
        if (aemObject != null && aemObject.getAttributes().containsKey(tag.getSrcXmlName())) {
            try {
                return TurMultiValue.singleItem(HtmlManipulator.html2Text(aemObject.getAttributes()
                        .get(tag.getSrcXmlName()).getValue().getString()));
            } catch (RepositoryException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return TurMultiValue.singleItem(EMPTY_STRING);
    }
}
