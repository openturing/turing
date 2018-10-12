package com.viglet.turing.api.sn.job;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;

@RestController
@RequestMapping("/api/sn/{id}/import")
@Api(tags = "Semantic Navigation Import", description = "Semantic Navigation Import API")
public class TurSNImportAPI {
	static final Logger logger = LogManager.getLogger(TurSNImportAPI.class.getName());
	@Autowired
	private JmsMessagingTemplate jmsMessagingTemplate;

	public static final String INDEXING_QUEUE = "indexing.queue";

	@PostMapping
	public String turSNImportBroker(@PathVariable String id, @RequestBody TurSNJobItems turSNJobItems)
			throws JSONException {
		TurSNJob turSNJob = new TurSNJob();
		turSNJob.setSiteId(id);
		turSNJob.setTurSNJobItems(turSNJobItems);
		send(turSNJob);
		return "Ok";

	}

	public void send(TurSNJob turSNJob) {
		logger.debug("Sent job - " + INDEXING_QUEUE);
		this.jmsMessagingTemplate.convertAndSend(INDEXING_QUEUE, turSNJob);

	}
}
