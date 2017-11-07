package com.viglet.turing.api.sn;


import javax.jms.Queue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.stereotype.Component;


@Component
@Path("sn/{snSiteId}/import")
public class TurSNImportAPI {

	@Autowired
	private JmsMessagingTemplate jmsMessagingTemplate;

	@Autowired
	private Queue queue;

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
		this.jmsMessagingTemplate.convertAndSend(this.queue, turSNJob);
		System.out.println("Sent job");
	}
}
