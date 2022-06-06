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
package com.viglet.turing.solr;

import org.apache.solr.common.SolrDocument;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.se.result.TurSEResult;

public class TurSolrUtils {

    private TurSolrUtils() {
        throw new IllegalStateException("Solr Utility class");
    }

    public static TurSEResult createTurSEResultFromDocument(SolrDocument document) {
        TurSEResult turSEResult = new TurSEResult();
        document.getFieldNames()
                .forEach(attribute -> turSEResult.getFields().put(attribute, document.getFieldValue(attribute)));
        return turSEResult;
    }
    
    public static int firstRowPositionFromCurrentPage(TurSEParameters turSEParameters) {
		return (turSEParameters.getCurrentPage() * turSEParameters.getRows()) - turSEParameters.getRows();
	}

	public static int lastRowPositionFromCurrentPage(TurSEParameters turSEParameters) {
		return (turSEParameters.getCurrentPage() * turSEParameters.getRows());
	}
}
