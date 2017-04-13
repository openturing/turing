package com.viglet.turing.api.ml.data.group;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

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
import javax.ws.rs.core.Response;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.viglet.turing.persistence.model.VigCategory;
import com.viglet.turing.persistence.model.VigData;
import com.viglet.turing.persistence.model.VigDataGroup;
import com.viglet.turing.persistence.model.VigDataGroupCategory;
import com.viglet.turing.persistence.model.VigDataGroupData;
import com.viglet.turing.persistence.model.VigDataSentence;
import com.viglet.turing.plugins.opennlp.OpenNLPConnector;

@Path("/ml/data/group")
public class VigMLDataGroupAPI {
	@GET
	@Produces("application/json")
	public Response dataGroups() throws JSONException {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();
		// Read the existing entries and write to console
		Query q = em.createQuery("SELECT dg FROM VigDataGroup dg");

		List<VigDataGroup> vigDataGroupList = q.getResultList();
		JSONArray vigDataGroups = new JSONArray();
		for (VigDataGroup vigDataGroup : vigDataGroupList) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", vigDataGroup.getId());
			jsonObject.put("name", vigDataGroup.getName());
			jsonObject.put("description", vigDataGroup.getDescription());
			vigDataGroups.put(jsonObject);

		}
		return Response.status(200).entity(vigDataGroups.toString()).build();
	}

	@POST
	@Path("create")
	@Produces("application/json")
	public Response create(@FormParam("name") String name, @FormParam("description") String description)
			throws JSONException {

		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;
		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();

		VigDataGroup vigDataGroup = new VigDataGroup();
		vigDataGroup.setName(name);
		vigDataGroup.setDescription(description);
		em.getTransaction().begin();
		em.persist(vigDataGroup);
		em.getTransaction().commit();
		
		JSONObject jsonObject = new JSONObject();
		JSONObject jsonDataGroup = new JSONObject();
		jsonDataGroup.put("id", vigDataGroup.getId());
		jsonDataGroup.put("name", vigDataGroup.getName());
		jsonDataGroup.put("description", vigDataGroup.getDescription());

		jsonObject.put("datagroup", jsonDataGroup);

		return Response.status(200).entity(jsonObject.toString()).build();
	}

	@GET
	@Path("{id}")
	@Produces("application/json")
	public Response dataGroup(@PathParam("id") int id) throws JSONException {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();
		// Read the existing entries and write to console
		Query q = em.createQuery("SELECT dg FROM VigDataGroup dg where dg.id = :id ").setParameter("id", id);

		VigDataGroup vigDataGroup = (VigDataGroup) q.getSingleResult();

		Query qDataGroupData = em
				.createQuery("SELECT dgd FROM VigDataGroupData dgd where dgd.vigDataGroup = :vigDataGroup ")
				.setParameter("vigDataGroup", vigDataGroup);

		List<VigDataGroupData> vigDataGroupDatas = qDataGroupData.getResultList();

		Query qDataGroupCategory = em
				.createQuery("SELECT dgc FROM VigDataGroupCategory dgc where dgc.vigDataGroup = :vigDataGroup ")
				.setParameter("vigDataGroup", vigDataGroup);

		List<VigDataGroupCategory> vigDataGroupCategories = qDataGroupCategory.getResultList();

		JSONObject jsonObject = new JSONObject();

		JSONArray jsonDatas = new JSONArray();
		for (VigDataGroupData vigDataGroupData : vigDataGroupDatas) {
			JSONObject jsonData = new JSONObject();
			VigData vigData = vigDataGroupData.getVigData();
			jsonData.put("id", vigData.getId());
			jsonData.put("name", vigData.getName());
			jsonData.put("type", vigData.getType());
			jsonDatas.put(jsonData);

		}

		JSONArray jsonCategories = new JSONArray();
		for (VigDataGroupCategory vigDataGroupCategory : vigDataGroupCategories) {
			JSONObject jsonCategory = new JSONObject();
			VigCategory vigCategory = vigDataGroupCategory.getVigCategory();
			jsonCategory.put("id", vigCategory.getId());
			jsonCategory.put("name", vigCategory.getName());
			jsonCategory.put("internal_name", vigCategory.getInternalName());
			jsonCategory.put("description", vigCategory.getDescription());
			jsonCategories.put(jsonCategory);

		}

		JSONObject jsonDataGroup = new JSONObject();
		jsonDataGroup.put("id", vigDataGroup.getId());
		jsonDataGroup.put("name", vigDataGroup.getName());
		jsonDataGroup.put("description", vigDataGroup.getDescription());

		jsonObject.put("datagroup", jsonDataGroup);
		jsonObject.put("data", jsonDatas);
		jsonObject.put("category", jsonCategories);
		return Response.status(200).entity(jsonObject.toString()).build();
	}
}
