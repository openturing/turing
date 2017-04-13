package com.viglet.turing.auth.google;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.services.plus.Plus.*;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.PeopleFeed;
import com.google.api.services.plus.model.Person;

/**
 * Get list of people user has shared with this app.
 */
public class PeopleServlet extends SigninServlet {
	  @Override
	    protected void doGet(HttpServletRequest request, HttpServletResponse response)
	        throws ServletException, IOException {
	      response.setContentType("application/json");

	      // Only fetch a list of people for connected users.
	      String tokenData = (String) request.getSession().getAttribute("token");
	      if (tokenData == null) {
	        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	        response.getWriter().print(GSON.toJson("Current user not connected."));
	        return;
	      }
	      try {
	        // Build credential from stored token data.
	        GoogleCredential credential = new GoogleCredential.Builder()
	            .setJsonFactory(JSON_FACTORY)
	            .setTransport(TRANSPORT)
	            .setClientSecrets(CLIENT_ID, CLIENT_SECRET).build()
	            .setFromTokenResponse(JSON_FACTORY.fromString(
	                tokenData, GoogleTokenResponse.class));
	        // Create a new authorized API client.
	        Plus service = new Plus.Builder(TRANSPORT, JSON_FACTORY, credential)
	            .setApplicationName(APPLICATION_NAME)
	            .build();
	        // Get a list of people that this user has shared with this app.	 
	        Person me = service.people().get("me").execute();
	        String name = me.getDisplayName();
	        String photo = me.getImage().getUrl();
	        PeopleFeed people = service.people().list("me", "visible").execute();
	        response.setStatus(HttpServletResponse.SC_OK);
	        response.getWriter().print(GSON.toJson(people));
	      } catch (IOException e) {
	        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	        response.getWriter().print(GSON.toJson("Failed to read data from Google. " +
	            e.getMessage()));
	      }
	    }
}
