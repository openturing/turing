package com.viglet.turing.api.se;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.viglet.turing.persistence.model.VigService;
import com.viglet.turing.solr.VigSolr;

@Path("/se1")
public class SEService {

	@GET
	@Produces("application/json")
	public Response list() throws JSONException {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();
		// Read the existing entries and write to console
		Query q = em.createQuery("SELECT s FROM VigService s where s.type = :type").setParameter("type", 3);

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
	public Response detail(@PathParam("id") int id) throws JSONException {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();
		// Read the existing entries and write to console
		Query q = em.createQuery("SELECT s FROM VigService s where s.type = :type and s.id = :id ")
				.setParameter("type", 3).setParameter("id", id);
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
		vigServiceJSON.put("se", jsonObject);

		return Response.status(200).entity(vigServiceJSON.toString()).build();
	}

	@GET
	@Path("select")
	@Produces("application/json")
	public Response select(@QueryParam("q") String q, @QueryParam("p") int p, @QueryParam("fq[]") List<String> fq)
			throws JSONException {
		String result = null;
		VigSolr vigSolr = new VigSolr();
		try {
			result = vigSolr.retrieveSolr(q, fq, p).toString();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return Response.status(200).entity(result).build();
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