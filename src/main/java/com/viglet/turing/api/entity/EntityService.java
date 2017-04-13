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

import com.viglet.turing.persistence.model.VigEntity;
import com.viglet.turing.persistence.model.VigTerm;
import com.viglet.turing.persistence.model.VigTermRelationFrom;
import com.viglet.turing.persistence.model.VigTermRelationTo;
import com.viglet.turing.persistence.model.VigTermVariation;
import com.viglet.turing.persistence.model.VigTermVariationLanguage;

@Path("/entity")
public class EntityService {

	@GET
	@Produces("application/json")
	public Response list() throws JSONException {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();
		// Read the existing entries and write to console
		Query q = em.createQuery("SELECT e FROM VigEntity e ");

		List<VigEntity> vigEntityList = q.getResultList();
		JSONArray vigEntities = new JSONArray();
		for (VigEntity vigEntity : vigEntityList) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", vigEntity.getId());
			jsonObject.put("name", vigEntity.getName());
			jsonObject.put("description", vigEntity.getDescription());
			vigEntities.put(jsonObject);

		}
		return Response.status(200).entity(vigEntities.toString()).build();
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
		Query q = em.createQuery("SELECT e FROM VigEntity e where e.id = :id ").setParameter("id", id);
		VigEntity vigEntity = (VigEntity) q.getSingleResult();
		JSONObject vigEntityJSON = new JSONObject();
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("id", vigEntity.getId());
		jsonObject.put("name", vigEntity.getName());
		jsonObject.put("description", vigEntity.getDescription());

		Query qTerms = em.createQuery("SELECT t FROM VigTerm t where t.vigEntity = :vigEntity")
				.setParameter("vigEntity", vigEntity);

		List<VigTerm> vigTermList = qTerms.getResultList();
		JSONArray vigTerms = new JSONArray();
		for (VigTerm vigTerm : vigTermList) {
			JSONObject jsonTerm = new JSONObject();
			jsonTerm.put("id", vigTerm.getId());
			jsonTerm.put("name", vigTerm.getName());
			vigTerms.put(jsonTerm);

		}

		jsonObject.put("terms", vigTerms);
		vigEntityJSON.put("entity", jsonObject);

		return Response.status(200).entity(vigEntityJSON.toString()).build();
	}

	@Path("{id}")
	@DELETE
	@Produces("application/json")
	public Response deleteEntity(@PathParam("id") int id) {
		JSONObject vigEntityJSON = new JSONObject();

		try {
			String PERSISTENCE_UNIT_NAME = "semantics-app";
			EntityManagerFactory factory;

			factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
			EntityManager em = factory.createEntityManager();

			// Read the existing entries and write to console
			Query q = em.createQuery("SELECT e FROM VigEntity e where e.id = :id ").setParameter("id", id);
			VigEntity vigEntity = (VigEntity) q.getSingleResult();

			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", vigEntity.getId());
			jsonObject.put("name", vigEntity.getName());
			jsonObject.put("description", vigEntity.getDescription());

			vigEntityJSON.put("entity", jsonObject);

			Query qTerms = em.createQuery("SELECT t FROM VigTerm t where t.vigEntity = :vigEntity ")
					.setParameter("vigEntity", vigEntity);
			List<?> terms = qTerms.getResultList();

			for (Object termObject : terms) {
				VigTerm vigTerm = (VigTerm) termObject;
				em.getTransaction().begin();
				for (VigTermRelationFrom vigTermRelationFrom : vigTerm.getVigTermRelationFroms()) {
					for (VigTermRelationTo vigTermRelationTo : vigTermRelationFrom.getVigTermRelationTos()) {
						em.remove(vigTermRelationTo);
					}
					em.remove(vigTermRelationFrom);
				}
				em.getTransaction().commit();
				em.getTransaction().begin();
				for (VigTermVariation vigTermVariation : vigTerm.getVigTermVariations()) {
					for (VigTermVariationLanguage vigTermVariationLanguage : vigTermVariation
							.getVigTermVariationLanguages()) {
						em.remove(vigTermVariationLanguage);
					}
					em.remove(vigTermVariation);
				}

				em.getTransaction().commit();

				em.getTransaction().begin();
				em.remove(vigTerm);
				em.getTransaction().commit();
			}
			em.getTransaction().begin();
			em.remove(vigEntity);
			em.getTransaction().commit();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Response.status(200).entity(vigEntityJSON.toString()).build();
	}
}