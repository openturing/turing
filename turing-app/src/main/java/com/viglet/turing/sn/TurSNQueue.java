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
package com.viglet.turing.sn;

import com.google.inject.Inject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import java.util.Enumeration;

@Component
public class TurSNQueue {
	private final JmsTemplate jmsTemplate;

	@Inject
	public TurSNQueue(JmsTemplate jmsTemplate) {
		this.jmsTemplate = jmsTemplate;
	}

	public int getQueueSize() {
		return jmsTemplate.browse(TurSNConstants.INDEXING_QUEUE, (session, browser) -> {
			Enumeration<?> messages = browser.getEnumeration();
			int total = 0;
			while (messages.hasMoreElements()) {
				messages.nextElement();
				total++;
			}
			return total;
		});

	}
}
