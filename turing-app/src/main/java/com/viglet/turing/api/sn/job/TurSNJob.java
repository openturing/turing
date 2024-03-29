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
package com.viglet.turing.api.sn.job;

import com.viglet.turing.client.sn.job.TurSNJobItems;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.io.Serial;
import java.io.Serializable;

@Setter
@Getter
@Component
public class TurSNJob implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String siteId;

	private TurSNJobItems turSNJobItems;

	public String toString() {
		return String.format("siteId: %s, turSNJobItems: %s", this.getSiteId(), this.getTurSNJobItems());
	}

}
