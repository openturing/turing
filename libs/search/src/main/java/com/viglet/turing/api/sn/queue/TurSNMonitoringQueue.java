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
