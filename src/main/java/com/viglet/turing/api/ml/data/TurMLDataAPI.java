package com.viglet.turing.api.ml.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
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

import org.json.JSONException;
import org.json.JSONObject;
import org.xml.sax.SAXException;

import com.viglet.turing.persistence.model.storage.TurData;
import com.viglet.turing.persistence.model.storage.TurDataSentence;
import com.viglet.turing.persistence.service.storage.TurDataSentenceService;
import com.viglet.turing.persistence.service.storage.TurDataService;
import com.viglet.turing.plugins.opennlp.TurOpenNLPConnector;

@Path("/ml/data")
public class TurMLDataAPI {
	TurDataService turDataService = new TurDataService();
	TurDataSentenceService turDataSentenceService = new TurDataSentenceService();

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurData> list() throws JSONException {
		return turDataService.listAll();
	}

	@Path("{dataId}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurData detail(@PathParam("dataId") int id) throws JSONException {
		return turDataService.get(id);
	}

	@Path("/{dataId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurData update(@PathParam("dataId") int id, TurData turData) throws Exception {
		TurData turDataEdit = turDataService.get(id);
		turDataEdit.setName(turData.getName());
		turDataEdit.setType(turData.getType());
		turDataService.save(turDataEdit);
		return turDataEdit;
	}

	@Path("/{dataId}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("dataId") int id) throws Exception {
		return turDataService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurData add(TurData turData) throws Exception {
		turDataService.save(turData);
		return turData;

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

		String sentences[] = TurOpenNLPConnector.sentenceDetect(handler.toString());

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
