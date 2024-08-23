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

import com.viglet.turing.client.sn.TurMultiValue;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurCmsTargetAttrValue {
	private String targetAttrName;
	private TurMultiValue multiValue;

	public TurCmsTargetAttrValue(String targetAttrName, TurMultiValue multiValue) {
		this.targetAttrName = targetAttrName;
		this.multiValue = multiValue;
	}

	public void addAllMultiValue(TurMultiValue multiValue) {
		this.multiValue.addAll(multiValue);
	}
	@Override
	public String toString() { 
	    return String.format("Target attribute name: %s, multiValue: %s", targetAttrName, multiValue);
	} 
}
