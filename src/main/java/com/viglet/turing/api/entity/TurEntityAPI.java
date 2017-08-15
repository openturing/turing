package com.viglet.turing.api.entity;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.turing.persistence.model.nlp.term.TurTerm;
import com.viglet.turing.persistence.model.nlp.term.TurTermRelationFrom;
import com.viglet.turing.persistence.model.nlp.term.TurTermRelationTo;
import com.viglet.turing.persistence.model.nlp.term.TurTermVariation;
import com.viglet.turing.persistence.model.nlp.term.TurTermVariationLanguage;
import com.viglet.turing.persistence.model.nlp.TurEntity;

@Path("/entity")
public class TurEntityAPI {

	@GET
	@Produces("application/json")
	public Response list() throws JSONException {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();
		// Read the existing entries and write to console
		Query q = em.createQuery("SELECT e FROM TurEntity e ");

		List<TurEntity> turEntityList = q.getResultList();
		JSONArray turEntities = new JSONArray();
		for (TurEntity turEntity : turEntityList) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", turEntity.getId());
			jsonObject.put("name", turEntity.getName());
			jsonObject.put("description", turEntity.getDescription());
			turEntities.put(jsonObject);

		}
		return Response.status(200).entity(turEntities.toString()).build();
	}

	@Path("{id}")
	@GET
	@Produces("application/json")
	public Response detail(@PathParam("id") int id) throws JSONException {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();
		// Read the existing entries and write to console
		Query q = em.createQuery("SELECT e FROM TurEntity e where e.id = :id ").setParameter("id", id);
		TurEntity turEntity = (TurEntity) q.getSingleResult();
		JSONObject turEntityJSON = new JSONObject();
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("id", turEntity.getId());
		jsonObject.put("name", turEntity.getName());
		jsonObject.put("description", turEntity.getDescription());

		Query qTerms = em.createQuery("SELECT t FROM TurTerm t where t.turEntity = :turEntity")
				.setParameter("turEntity", turEntity);

		List<TurTerm> turTermList = qTerms.getResultList();
		JSONArray turTerms = new JSONArray();
		for (TurTerm turTerm : turTermList) {
			JSONObject jsonTerm = new JSONObject();
			jsonTerm.put("id", turTerm.getId());
			jsonTerm.put("name", turTerm.getName());
			turTerms.put(jsonTerm);

		}

		jsonObject.put("terms", turTerms);
		turEntityJSON.put("entity", jsonObject);

		return Response.status(200).entity(turEntityJSON.toString()).build();
	}

	@Path("{id}")
	@DELETE
	@Produces("application/json")
	public Response deleteEntity(@PathParam("id") int id) {
		JSONObject turEntityJSON = new JSONObject();

		try {
			String PERSISTENCE_UNIT_NAME = "semantics-app";
			EntityManagerFactory factory;

			factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
			EntityManager em = factory.createEntityManager();

			// Read the existing entries and write to console
			Query q = em.createQuery("SELECT e FROM TurEntity e where e.id = :id ").setParameter("id", id);
			TurEntity turEntity = (TurEntity) q.getSingleResult();

			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", turEntity.getId());
			jsonObject.put("name", turEntity.getName());
			jsonObject.put("description", turEntity.getDescription());

			turEntityJSON.put("entity", jsonObject);

			Query qTerms = em.createQuery("SELECT t FROM TurTerm t where t.turEntity = :turEntity ")
					.setParameter("turEntity", turEntity);
			List<?> terms = qTerms.getResultList();

			for (Object termObject : terms) {
				TurTerm turTerm = (TurTerm) termObject;
				em.getTransaction().begin();
				for (TurTermRelationFrom turTermRelationFrom : turTerm.getTurTermRelationFroms()) {
					for (TurTermRelationTo turTermRelationTo : turTermRelationFrom.getTurTermRelationTos()) {
						em.remove(turTermRelationTo);
					}
					em.remove(turTermRelationFrom);
				}
				em.getTransaction().commit();
				em.getTransaction().begin();
				for (TurTermVariation turTermVariation : turTerm.getTurTermVariations()) {
					for (TurTermVariationLanguage turTermVariationLanguage : turTermVariation
							.getTurTermVariationLanguages()) {
						em.remove(turTermVariationLanguage);
					}
					em.remove(turTermVariation);
				}

				em.getTransaction().commit();

				em.getTransaction().begin();
				em.remove(turTerm);
				em.getTransaction().commit();
			}
			em.getTransaction().begin();
			em.remove(turEntity);
			em.getTransaction().commit();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Response.status(200).entity(turEntityJSON.toString()).build();
	}
}