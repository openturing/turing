package com.viglet.turing.api;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Path("")
public class TurAPI {

	@Autowired
	TurAPIBean turAPIBean;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurAPIBean info() throws JSONException {

		turAPIBean.setProduct("Viglet Turing");

		return turAPIBean;
	}
}