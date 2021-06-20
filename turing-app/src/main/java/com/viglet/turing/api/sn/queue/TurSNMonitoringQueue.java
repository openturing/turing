/*
 * Copyright (C) 2016-2019 the original author or authors. 
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

package com.viglet.turing.api.sn.queue;

import java.util.Enumeration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queue")
public class TurSNMonitoringQueue {

	@Autowired
	JmsTemplate jmsTemplate;

	public static final String INDEXING_QUEUE = "indexing.queue";

	@GetMapping
	public String turMonitoringQueue() {

		return jmsTemplate.browse(INDEXING_QUEUE, (session, browser) -> {
			Enumeration<?> messages = browser.getEnumeration();
			int total = 0;
			while (messages.hasMoreElements()) {
				messages.nextElement();
				total++;
			}
			return String.format("Total %d elements waiting in %s", total, INDEXING_QUEUE);
		});

	}
}
