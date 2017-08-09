package com.viglet.turing.api.se;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.service.se.TurSEVendorService;

@Path("/se/vendor")
public class TurSEVendorAPI {
	TurSEVendorService turSEVendorService = new TurSEVendorService();
	
	@GET
	@Produces("application/json")
	public Response listInstances() throws JSONException {
		List<TurSEVendor> turSEVendorList = turSEVendorService.listAll();
	
		JSONArray vigSolutions = new JSONArray();
		for (TurSEVendor turSEVendor : turSEVendorList) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", turSEVendor.getId());
			jsonObject.put("title", turSEVendor.getTitle());
			jsonObject.put("description", turSEVendor.getDescription());
			jsonObject.put("website", turSEVendor.getWebsite());
			vigSolutions.put(jsonObject);

		}
		return Response.status(200).entity(vigSolutions.toString()).build();
	}

	@Path("{id}")
	@GET
	@Produces("application/json")
	public Response seSolution(@PathParam("id") String id) throws JSONException {
		TurSEVendor turSEVendor = turSEVendorService.get(id);
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("id", turSEVendor.getId());
		jsonObject.put("title", turSEVendor.getTitle());
		jsonObject.put("description", turSEVendor.getDescription());
		jsonObject.put("website", turSEVendor.getWebsite());
		return Response.status(200).entity(jsonObject.toString()).build();
	}
}
