package com.viglet.turing.api.sn;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@Path("sn/{snSiteId}/import")
public class TurSNImportAPI {
	static final Logger logger = LogManager.getLogger(TurSNImportAPI.class.getName());
	@Autowired
	private JmsMessagingTemplate jmsMessagingTemplate;

	public static final String INDEXING_QUEUE = "indexing.queue";
    public static final String NLP_QUEUE = "nlp.queue";

    
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response broker(@PathParam("snSiteId") String id, String json) throws JSONException {
		TurSNJob turSNJob = new TurSNJob();
		turSNJob.setSiteId(id);
		turSNJob.setJson(json);
		send(turSNJob);
		return Response.status(200).entity("Ok").build();

	}

	public void send(TurSNJob turSNJob) {
		logger.debug("Sent job - " + INDEXING_QUEUE);
		this.jmsMessagingTemplate.convertAndSend(INDEXING_QUEUE, turSNJob);
		
	}
}
