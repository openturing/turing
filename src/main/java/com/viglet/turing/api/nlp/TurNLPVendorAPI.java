package com.viglet.turing.api.nlp;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.service.nlp.TurNLPInstanceService;
import com.viglet.turing.persistence.service.nlp.TurNLPVendorService;

@Path("/nlp/vendor")
public class TurNLPVendorAPI {
	TurNLPVendorService turNLPVendorService = new TurNLPVendorService();
	
	@GET
	@Produces("application/json")
	public Response listInstances() throws JSONException {
		List<TurNLPVendor> turNLPVendorList = turNLPVendorService.listAll();
	
		JSONArray vigSolutions = new JSONArray();
		for (TurNLPVendor turNLPVendor : turNLPVendorList) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", turNLPVendor.getId());
			jsonObject.put("title", turNLPVendor.getTitle());
			jsonObject.put("description", turNLPVendor.getDescription());
			jsonObject.put("website", turNLPVendor.getWebsite());
			vigSolutions.put(jsonObject);

		}
		return Response.status(200).entity(vigSolutions.toString()).build();
	}

	@Path("{id}")
	@GET
	@Produces("application/json")
	public Response nlpSolution(@PathParam("id") String id) throws JSONException {
		TurNLPVendor turNLPVendor = turNLPVendorService.get(id);
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("id", turNLPVendor.getId());
		jsonObject.put("title", turNLPVendor.getTitle());
		jsonObject.put("description", turNLPVendor.getDescription());
		jsonObject.put("website", turNLPVendor.getWebsite());
		return Response.status(200).entity(jsonObject.toString()).build();
	}
}
