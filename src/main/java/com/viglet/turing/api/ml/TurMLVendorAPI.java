package com.viglet.turing.api.ml;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.turing.persistence.model.ml.TurMLVendor;
import com.viglet.turing.persistence.service.ml.TurMLVendorService;

@Path("/ml/vendor")
public class TurMLVendorAPI {
	TurMLVendorService turMLVendorService = new TurMLVendorService();
	
	@GET
	@Produces("application/json")
	public Response listInstances() throws JSONException {
		List<TurMLVendor> turMLVendorList = turMLVendorService.listAll();
	
		JSONArray vigSolutions = new JSONArray();
		for (TurMLVendor turMLVendor : turMLVendorList) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", turMLVendor.getId());
			jsonObject.put("title", turMLVendor.getTitle());
			jsonObject.put("description", turMLVendor.getDescription());
			jsonObject.put("website", turMLVendor.getWebsite());
			vigSolutions.put(jsonObject);

		}
		return Response.status(200).entity(vigSolutions.toString()).build();
	}

	@Path("{id}")
	@GET
	@Produces("application/json")
	public Response mlSolution(@PathParam("id") String id) throws JSONException {
		TurMLVendor turMLVendor = turMLVendorService.get(id);
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("id", turMLVendor.getId());
		jsonObject.put("title", turMLVendor.getTitle());
		jsonObject.put("description", turMLVendor.getDescription());
		jsonObject.put("website", turMLVendor.getWebsite());
		return Response.status(200).entity(jsonObject.toString()).build();
	}
}
