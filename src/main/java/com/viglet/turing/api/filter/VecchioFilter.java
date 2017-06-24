package com.viglet.turing.api.filter;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import javax.servlet.ServletContext;
import javax.ws.rs.ext.Provider;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;

@Provider
public class VecchioFilter implements ContainerRequestFilter {
	@javax.ws.rs.core.Context
	ServletContext context;
	URL vecchioURL = null;

	@Override
	public ContainerRequest filter(ContainerRequest containerRequest) {
		System.out.println("ContainerRequest");

		return containerRequest;
	}

}