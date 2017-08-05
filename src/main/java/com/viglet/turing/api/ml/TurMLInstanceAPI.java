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

import com.viglet.turing.persistence.model.ml.TurMLInstance;
import com.viglet.turing.persistence.service.ml.TurMLInstanceService;

@Path("/ml")
public class TurMLInstanceAPI {
	TurMLInstanceService turMLInstanceService = new TurMLInstanceService();

	@GET
	@Produces("application/json")
	public Response listInstances() throws JSONException {
		List<TurMLInstance> turMLInstanceList = turMLInstanceService.listAll();
		JSONArray vigServices = new JSONArray();
		for (TurMLInstance turMLInstance : turMLInstanceList) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", turMLInstance.getId());
			jsonObject.put("title", turMLInstance.getTitle());
			jsonObject.put("description", turMLInstance.getDescription());
			jsonObject.put("vendor", turMLInstance.getTurMLVendor().getId());
			jsonObject.put("host", turMLInstance.getHost());
			jsonObject.put("port", turMLInstance.getPort());
			jsonObject.put("enabled", turMLInstance.getEnabled() == 1 ? true : false);
			jsonObject.put("selected", turMLInstance.getSelected() == 1 ? true : false);
			vigServices.put(jsonObject);

		}
		return Response.status(200).entity(vigServices.toString()).build();
	}

	@Path("{id}")
	@GET
	@Produces("application/json")
	public Response detailService(@PathParam("id") int id) throws JSONException {
		TurMLInstance turMLInstance = turMLInstanceService.get(id);
		JSONObject vigServiceJSON = new JSONObject();
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("id", turMLInstance.getId());
		jsonObject.put("title", turMLInstance.getTitle());
		jsonObject.put("description", turMLInstance.getDescription());
		jsonObject.put("vendor", turMLInstance.getTurMLVendor().getId());
		jsonObject.put("host", turMLInstance.getHost());
		jsonObject.put("port", turMLInstance.getPort());
		jsonObject.put("enabled", turMLInstance.getEnabled() == 1 ? true : false);
		jsonObject.put("selected", turMLInstance.getSelected() == 1 ? true : false);
		vigServiceJSON.put("ml", jsonObject);
		return Response.status(200).entity(vigServiceJSON.toString()).build();
	}
}