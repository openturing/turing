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

package com.viglet.turing.converse.exchange.intent;

import lombok.Getter;

@Getter
public class TurConverseIntentPhraseDataExchange {

	private String text;

	private String alias;

	private String meta;

	private boolean userDefined;

	public void setText(String text) {
		this.text = text;
	}

	public void setAlias(String alias) {
		this.alias = alias;
	}

	public void setMeta(String meta) {
		this.meta = meta;
	}

	public void setUserDefined(boolean userDefined) {
		this.userDefined = userDefined;
	}

}
