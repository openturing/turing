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
package com.viglet.turing.connector.cms.beans;

import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.mappers.MappingDefinitions;

public class TurAttrDefContext {
	private Object cmsObjectInstance;
	private TuringTag turingTag;
	private IHandlerConfiguration iHandlerConfiguration;
	private MappingDefinitions mappingDefinitions;

	public TurAttrDefContext(TurAttrDefContext turAttrDefContext) {
		this.setCMSObjectInstance(turAttrDefContext.getCMSObjectInstance());
		this.setiHandlerConfiguration(turAttrDefContext.getiHandlerConfiguration());
		this.setMappingDefinitions(turAttrDefContext.getMappingDefinitions());
		this.setTuringTag(turAttrDefContext.getTuringTag());
	}

	public TurAttrDefContext(Object cmsObjectInstance, TuringTag turingTag,
			IHandlerConfiguration iHandlerConfiguration, MappingDefinitions mappingDefinitions) {
		this.setCMSObjectInstance(cmsObjectInstance);
		this.setiHandlerConfiguration(iHandlerConfiguration);
		this.setMappingDefinitions(mappingDefinitions);
		this.setTuringTag(turingTag);
	}

	public Object getCMSObjectInstance() {
		return cmsObjectInstance;
	}

	public void setCMSObjectInstance(Object cmsObjectInstance) {
		this.cmsObjectInstance = cmsObjectInstance;
	}

	public TuringTag getTuringTag() {
		return turingTag;
	}

	public void setTuringTag(TuringTag turingTag) {
		this.turingTag = turingTag;
	}

	public IHandlerConfiguration getiHandlerConfiguration() {
		return iHandlerConfiguration;
	}

	public void setiHandlerConfiguration(IHandlerConfiguration iHandlerConfiguration) {
		this.iHandlerConfiguration = iHandlerConfiguration;
	}

	public MappingDefinitions getMappingDefinitions() {
		return mappingDefinitions;
	}

	public void setMappingDefinitions(MappingDefinitions mappingDefinitions) {
		this.mappingDefinitions = mappingDefinitions;
	}
}
