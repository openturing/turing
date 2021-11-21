/*
 * Copyright (C) 2016-2021 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.sn;

import java.util.Enumeration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class TurSNQueue {
	@Autowired
	JmsTemplate jmsTemplate;

	public static final String INDEXING_QUEUE = "indexing.queue";

	public int getQueueSize() {
		return jmsTemplate.browse(INDEXING_QUEUE, (session, browser) -> {
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
