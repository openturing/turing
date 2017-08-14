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
import javax.ws.rs.core.MediaType;
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

import com.viglet.turing.persistence.model.storage.TurData;
import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataSentence;
import com.viglet.turing.persistence.service.storage.TurDataGroupService;
import com.viglet.turing.persistence.service.storage.TurDataSentenceService;
import com.viglet.turing.persistence.service.storage.TurDataService;
import com.viglet.turing.plugins.opennlp.OpenNLPConnector;

@Path("/ml/data")
public class TurMLDataAPI {
	TurDataService turDataService = new TurDataService();
	TurDataSentenceService turDataSentenceService = new TurDataSentenceService();
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurData> list() throws JSONException {
		return turDataService.listAll();	
	}


	@Path("{id}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurData detail(@PathParam("id") int id) throws JSONException {
		return turDataService.get(id);		
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

		TurData turData = new TurData();
		turData.setName("The-Problems-of-Philosophy-LewisTheme.pdf");
		turData.setType("pdf");
		turDataService.save(turData);

		for (String sentence : sentences) {
			TurDataSentence turDataSentence = new TurDataSentence();
			turDataSentence.setTurData(turData);
			turDataSentence.setSentence(sentence);
			turDataSentenceService.save(turDataSentence);
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
