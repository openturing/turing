package com.viglet.turing.api.se;

import java.util.List;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.service.se.TurSEInstanceService;
import com.viglet.turing.solr.VigSolr;

@Path("/se")
public class TurSEInstanceAPI {
	TurSEInstanceService turSEInstanceService = new TurSEInstanceService();

	@GET
	@Produces("application/json")
	public Response listInstances() throws JSONException {
		List<TurSEInstance> turSEInstanceList = turSEInstanceService.listAll();
		JSONArray vigServices = new JSONArray();
		for (TurSEInstance turSEInstance : turSEInstanceList) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", turSEInstance.getId());
			jsonObject.put("title", turSEInstance.getTitle());
			jsonObject.put("description", turSEInstance.getDescription());
			jsonObject.put("vendor", turSEInstance.getTurSEVendor().getId());
			jsonObject.put("host", turSEInstance.getHost());
			jsonObject.put("port", turSEInstance.getPort());
			jsonObject.put("enabled", turSEInstance.getEnabled() == 1 ? true : false);
			jsonObject.put("selected", turSEInstance.getSelected() == 1 ? true : false);
			vigServices.put(jsonObject);

		}
		return Response.status(200).entity(vigServices.toString()).build();
	}

	@Path("{id}")
	@GET
	@Produces("application/json")
	public Response detailService(@PathParam("id") int id) throws JSONException {
		TurSEInstance turSEInstance = turSEInstanceService.get(id);
		JSONObject vigServiceJSON = new JSONObject();
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("id", turSEInstance.getId());
		jsonObject.put("title", turSEInstance.getTitle());
		jsonObject.put("description", turSEInstance.getDescription());
		jsonObject.put("vendor", turSEInstance.getTurSEVendor().getId());
		jsonObject.put("host", turSEInstance.getHost());
		jsonObject.put("port", turSEInstance.getPort());
		jsonObject.put("enabled", turSEInstance.getEnabled() == 1 ? true : false);
		jsonObject.put("selected", turSEInstance.getSelected() == 1 ? true : false);
		vigServiceJSON.put("se", jsonObject);
		return Response.status(200).entity(vigServiceJSON.toString()).build();
	}
	
	@POST
	@Path("update")
	@Produces("application/json")
	public Response update(@FormParam("vigText") String vigText, @FormParam("vigNLP") int turNLPInstanceId,
			@FormParam("vigSE") String vigSE) throws JSONException {

		String text = vigText;
		String result = null;
		int se = 0;
		if (this.isNumeric(vigSE)) {
			se = Integer.parseInt(vigSE);
		}
		VigSolr vigSolr = new VigSolr(turNLPInstanceId, se, text);
		try {
			result = vigSolr.indexing();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Response.status(200).entity(result).build();
	}

	public boolean isNumeric(String str) {
		return str.matches("-?\\d+(\\.\\d+)?"); // match a number with optional
												// '-' and decimal.
	}
}