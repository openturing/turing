package com.viglet.turing.api.test;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

import java.util.Enumeration;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import org.json.JSONException;

@Path("/cross")
public class TestCrossContext {
	@Context
	ServletContext context;

	@GET
	@Produces("application/json")
	public Response list() throws JSONException {
		
		ServletContext context1 = context.getContext("/vecchio");
		context1.getServletRegistration("aa");
		return Response.status(200).entity(context1.getClassLoader().toString()).build();
	}
}
