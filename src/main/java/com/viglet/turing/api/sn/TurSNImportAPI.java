package com.viglet.turing.api.sn;


import javax.jms.Queue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsMessagingTemplate;
import org.springframework.stereotype.Component;


@Component
@Path("sn/import")
public class TurSNImportAPI {

	@Autowired
	private JmsMessagingTemplate jmsMessagingTemplate;

	@Autowired
	private Queue queue;

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response broker(String json) throws JSONException {
		send(json);
		return Response.status(200).entity("Ok").build();

	}

	public void send(String json) {
		this.jmsMessagingTemplate.convertAndSend(this.queue, json);
		System.out.println("Sent message");
	}
}
