package com.viglet.turing.api.entity;

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

import com.viglet.turing.persistence.model.nlp.TurNLPVendor;

@Path("/entity/terms")
public class NLPVendor {
	@GET
	@Produces("application/json")
	public Response nlpSolution() throws JSONException {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();
		// Read the existing entries and write to console
		Query q = em.createQuery("SELECT s FROM VigTerm s");

		List<TurNLPVendor> vigNLPVednorList = q.getResultList();
		JSONArray vigSolutions = new JSONArray();
		for (TurNLPVendor turNLPVendor : vigNLPVednorList) {
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
	public Response nlpSolution(@PathParam("id") int id) throws JSONException {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();
		// Read the existing entries and write to console
		Query q = em.createQuery("SELECT s FROM VigNLPVendor s where s.id = :id ").setParameter("id", id);
		TurNLPVendor turNLPVendor = (TurNLPVendor) q.getSingleResult();
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("id", turNLPVendor.getId());
		jsonObject.put("title", turNLPVendor.getTitle());
		jsonObject.put("description", turNLPVendor.getDescription());
		jsonObject.put("website", turNLPVendor.getWebsite());
		return Response.status(200).entity(jsonObject.toString()).build();
	}
}
