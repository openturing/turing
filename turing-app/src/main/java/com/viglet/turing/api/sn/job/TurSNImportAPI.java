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

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

import io.swagger.annotations.Api;

@RestController
@RequestMapping("/api/sn/{siteName}/import")
@Api(tags = "Semantic Navigation Import", description = "Semantic Navigation Import API")
public class TurSNImportAPI {
	static final Logger logger = LogManager.getLogger(TurSNImportAPI.class.getName());
	@Autowired
	private JmsMessagingTemplate jmsMessagingTemplate;
	@Autowired
	private TurSNSiteRepository turSNSiteRepository;

	public static final String INDEXING_QUEUE = "indexing.queue";

	@PostMapping
	public boolean turSNImportBroker(@PathVariable String siteName, @RequestBody TurSNJobItems turSNJobItems)
			throws JSONException {
		TurSNSite turSNSite = turSNSiteRepository.findByName(siteName);
		if (turSNSite != null) {
			TurSNJob turSNJob = new TurSNJob();
			turSNJob.setSiteId(turSNSite.getId());
			turSNJob.setTurSNJobItems(turSNJobItems);
			send(turSNJob);
			return true;
		} else {
			return false;
		}

	}

	public void send(TurSNJob turSNJob) {

		sentQueueInfo(turSNJob);

		if (logger.isDebugEnabled()) {
			logger.debug("Sent job - " + INDEXING_QUEUE);
			logger.debug("turSNJob: " + turSNJob.getTurSNJobItems().toString());
		}
		this.jmsMessagingTemplate.convertAndSend(INDEXING_QUEUE, turSNJob);

	}

	private void sentQueueInfo(TurSNJob turSNJob) {
		TurSNSite turSNSite = turSNSiteRepository.findById(turSNJob.getSiteId()).orElse(null);
		turSNJob.getTurSNJobItems().forEach(turJobItem -> {
			if (turSNSite != null && turJobItem != null && turJobItem.getAttributes() != null
					&& turJobItem.getAttributes().containsKey("id")) {
				String action = null;
				if (turJobItem.getTurSNJobAction().equals(TurSNJobAction.CREATE)) {
					action = "index";
				} else if (turJobItem.getTurSNJobAction().equals(TurSNJobAction.DELETE)) {
					action = "deindex";
				}
				logger.info(String.format("Sent to queue to %s the Object ID '%s' of '%s' SN Site.", action,
						turJobItem.getAttributes().get("id"), turSNSite.getName()));

			}
		});
	}
}
