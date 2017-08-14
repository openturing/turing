package com.viglet.turing.api.nlp;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.service.nlp.TurNLPInstanceService;

@Path("/nlp")
public class TurNLPInstanceAPI {
	TurNLPInstanceService turNLPInstanceService = new TurNLPInstanceService();

	@GET
	@Produces("application/json")
	public Response listInstances() throws JSONException {
		List<TurNLPInstance> turNLPInstanceList = turNLPInstanceService.listAll();
		JSONArray vigServices = new JSONArray();
		for (TurNLPInstance turNLPInstance : turNLPInstanceList) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", turNLPInstance.getId());
			jsonObject.put("title", turNLPInstance.getTitle());
			jsonObject.put("description", turNLPInstance.getDescription());
			jsonObject.put("vendor", turNLPInstance.getTurNLPVendor().getId());
			jsonObject.put("host", turNLPInstance.getHost());
			jsonObject.put("port", turNLPInstance.getPort());
			jsonObject.put("enabled", turNLPInstance.getEnabled() == 1 ? true : false);
			jsonObject.put("selected", turNLPInstance.getSelected() == 1 ? true : false);
			vigServices.put(jsonObject);

		}
		return Response.status(200).entity(vigServices.toString()).build();
	}

	@Path("{id}")
	@GET
	@Produces("application/json")
	public Response detailService(@PathParam("id") int id) throws JSONException {
		TurNLPInstance turNLPInstance = turNLPInstanceService.get(id);
		JSONObject vigServiceJSON = new JSONObject();
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("id", turNLPInstance.getId());
		jsonObject.put("title", turNLPInstance.getTitle());
		jsonObject.put("description", turNLPInstance.getDescription());
		jsonObject.put("vendor", turNLPInstance.getTurNLPVendor().getId());
		jsonObject.put("host", turNLPInstance.getHost());
		jsonObject.put("port", turNLPInstance.getPort());
		jsonObject.put("enabled", turNLPInstance.getEnabled() == 1 ? true : false);
		jsonObject.put("selected", turNLPInstance.getSelected() == 1 ? true : false);
		vigServiceJSON.put("nlp", jsonObject);
		return Response.status(200).entity(vigServiceJSON.toString()).build();
	}
}