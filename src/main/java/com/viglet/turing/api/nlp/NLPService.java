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

import com.viglet.turing.persistence.model.VigService;

@Path("/nlp")
public class NLPService {

	@GET
	@Produces("application/json")
	public Response listServices() throws JSONException {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();
		// Read the existing entries and write to console
		Query q = em.createQuery("SELECT s FROM VigService s where s.type = :type").setParameter("type", 2);

		List<VigService> vigServiceList = q.getResultList();
		JSONArray vigServices = new JSONArray();
		for (VigService vigService : vigServiceList) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", vigService.getId());
			jsonObject.put("type", vigService.getType());
			jsonObject.put("solution", vigService.getSub_type());
			jsonObject.put("title", vigService.getTitle());
			jsonObject.put("description", vigService.getDescription());
			jsonObject.put("host", vigService.getHost());
			jsonObject.put("port", vigService.getPort());
			jsonObject.put("enabled", vigService.getEnabled() == 1 ? true : false);
			vigServices.put(jsonObject);

		}
		return Response.status(200).entity(vigServices.toString()).build();
	}

	@Path("{id}")
	@GET
	@Produces("application/json")
	public Response detailService(@PathParam("id") int id) throws JSONException {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();
		// Read the existing entries and write to console
		Query q = em.createQuery("SELECT s FROM VigService s where s.type = :type and s.id = :id ")
				.setParameter("type", 2).setParameter("id", id);
		VigService vigService = (VigService) q.getSingleResult();
		JSONObject vigServiceJSON = new JSONObject();
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("id", vigService.getId());
		jsonObject.put("type", vigService.getType());
		jsonObject.put("solution", vigService.getSub_type());
		jsonObject.put("title", vigService.getTitle());
		jsonObject.put("description", vigService.getDescription());
		jsonObject.put("host", vigService.getHost());
		jsonObject.put("port", vigService.getPort());
		jsonObject.put("enabled", vigService.getEnabled() == 1 ? true : false);
		vigServiceJSON.put("nlp", jsonObject);
		return Response.status(200).entity(vigServiceJSON.toString()).build();
	}
}