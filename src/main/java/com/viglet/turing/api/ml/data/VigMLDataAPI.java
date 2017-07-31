package com.viglet.turing.api.ml.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

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

@Path("/ml/data")
public class VigMLDataAPI {
	@GET
	@Produces("application/json")
	public Response datas() throws JSONException {
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();
		// Read the existing entries and write to console
		Query q = em.createQuery("SELECT d FROM VigData d");

		List<VigData> vigDataList = q.getResultList();
		JSONArray vigDatas = new JSONArray();
		for (VigData vigData : vigDataList) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("id", vigData.getId());
			jsonObject.put("name", vigData.getName());
			jsonObject.put("type", vigData.getType());
			vigDatas.put(jsonObject);

		}
		return Response.status(200).entity(vigDatas.toString()).build();
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
		Query q = em.createQuery("SELECT e FROM VigData e where e.id = :id ").setParameter("id", id);
		VigData vigData = (VigData) q.getSingleResult();
		JSONObject jsonData = new JSONObject();
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("id", vigData.getId());
		jsonObject.put("name", vigData.getName());
		jsonObject.put("type", vigData.getType());

		Query qDataSentence = em.createQuery("SELECT tds FROM VigDataSentence tds where tds.vigData = :vigData")
				.setParameter("vigData", vigData);

		List<VigDataSentence> vigDataSentenceList = qDataSentence.getResultList();
		
	

		
		JSONArray vigSentences = new JSONArray();
		for (VigDataSentence vigTrainDocSentence : vigDataSentenceList) {
			JSONObject jsonTerm = new JSONObject();
			jsonTerm.put("id", vigTrainDocSentence.getId());
			jsonTerm.put("sentence", vigTrainDocSentence.getSentence());
			vigSentences.put(jsonTerm);

		}
		List<VigDataGroupCategory> vigDataGroupCategories = vigData.getVigDataGroupData().get(0).getVigDataGroup().getVigDataGroupCategories();
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

		jsonObject.put("sentence", vigSentences);
		jsonData.put("data", jsonObject);
		jsonData.put("category", jsonCategories);

		return Response.status(200).entity(jsonData.toString()).build();
	}

	@GET
	@Path("import")
	@Produces("application/json")
	public Response importData() throws JSONException, IOException, SAXException, TikaException {

		BodyContentHandler handler = new BodyContentHandler(-1);
		Metadata metadata = new Metadata();
		FileInputStream inputstream = new FileInputStream(
				new File("/Users/alexandreoliveira/Desktop/The-Problems-of-Philosophy-LewisTheme.pdf"));
		ParseContext pcontext = new ParseContext();

		// parsing the document using PDF parser
		PDFParser pdfparser = new PDFParser();
		pdfparser.parse(inputstream, handler, metadata, pcontext);

		// getting the content of the document
		System.out.println("Contents of the PDF :" + handler.toString());

		String sentences[] = OpenNLPConnector.sentenceDetect(handler.toString());

		// JPA
		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		EntityManager em = factory.createEntityManager();

		VigData vigData = new VigData();
		vigData.setName("The-Problems-of-Philosophy-LewisTheme.pdf");
		vigData.setType("pdf");

		em.getTransaction().begin();
		em.persist(vigData);
		em.getTransaction().commit();

		for (String sentence : sentences) {
			VigDataSentence vigTrainDocSentence = new VigDataSentence();
			vigTrainDocSentence.setVigData(vigData);
			vigTrainDocSentence.setSentence(sentence);
			em.getTransaction().begin();
			em.persist(vigTrainDocSentence);
			em.getTransaction().commit();
		}

		// getting metadata of the document
		System.out.println("Metadata of the PDF:");
		String[] metadataNames = metadata.names();

		for (String name : metadataNames) {
			System.out.println(name + " : " + metadata.get(name));
		}
		JSONObject jsonTraining = new JSONObject();
		jsonTraining.put("sentences", sentences);
		return Response.status(200).entity(jsonTraining.toString()).build();
	}
}
