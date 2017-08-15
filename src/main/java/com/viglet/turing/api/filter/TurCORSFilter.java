package com.viglet.turing.api.filter;

import java.net.URL;

import javax.servlet.ServletContext;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;

public class TurCORSFilter implements ContainerResponseFilter {
	@javax.ws.rs.core.Context
	ServletContext context;
	URL vecchioURL = null;

	@Override
	public ContainerResponse filter(ContainerRequest containerRequest, ContainerResponse containerResponse) {

		ResponseBuilder responseBuilder = Response.fromResponse(containerResponse.getResponse());
		responseBuilder.header("Access-Control-Allow-Origin", "*");
		responseBuilder.header("Access-Control-Allow-Methods", "POST, GET, OPTIONS, PUT, DELETE");
		responseBuilder.header("Access-Control-Allow-Headers", "Content-type");
		responseBuilder.header("Access-Control-Max-Age", "86400");
		responseBuilder.header("Content-Length",
				Integer.getInteger(containerResponse.getHeaderValue("X-Content-Length")));
		containerResponse.setResponse(responseBuilder.build());
		return containerResponse;
	}
}