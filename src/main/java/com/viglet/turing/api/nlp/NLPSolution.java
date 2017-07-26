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

import com.viglet.turing.persistence.model.TurNLPSolution;

@Path("/nlp/solution")
public class NLPSolution {
	@GET
	@Produces("application/json")
	public Response nlpSolution() throws JSONException {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();
		// Read the existing entries and write to console
		Query q = em.createQuery("SELECT s FROM TurNLPSolution s");

		List<TurNLPSolution> vigSolutionList = q.getResultList();
		JSONArray vigSolutions = new JSONArray();
		for (TurNLPSolution vigSolution : vigSolutionList) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", vigSolution.getId());
			jsonObject.put("title", vigSolution.getTitle());
			jsonObject.put("description", vigSolution.getDescription());
			jsonObject.put("website", vigSolution.getWebsite());
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
		Query q = em.createQuery("SELECT s FROM TurNLPSolution s where s.id = :id ").setParameter("id", id);
		TurNLPSolution vigSolution = (TurNLPSolution) q.getSingleResult();
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("id", vigSolution.getId());
		jsonObject.put("title", vigSolution.getTitle());
		jsonObject.put("description", vigSolution.getDescription());
		jsonObject.put("website", vigSolution.getWebsite());
		return Response.status(200).entity(jsonObject.toString()).build();
	}
}
