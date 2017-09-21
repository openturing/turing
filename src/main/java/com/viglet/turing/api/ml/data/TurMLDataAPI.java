package com.viglet.turing.api.ml.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import com.viglet.turing.persistence.model.storage.TurData;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;

import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;
import com.viglet.turing.persistence.repository.storage.TurDataRepository;
import com.viglet.turing.plugins.opennlp.TurOpenNLPConnector;

@Component
@Path("ml/data")
public class TurMLDataAPI {
	
	@Autowired
	TurDataRepository turDataRepository;
	@Autowired
	TurDataGroupSentenceRepository turDataGroupSentenceRepository;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurData> list() throws JSONException {
		return this.turDataRepository.findAll();
	}

	@Path("{dataId}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurData detail(@PathParam("dataId") int id) throws JSONException {
		return this.turDataRepository.getOne(id);
	}

	@Path("/{dataId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurData update(@PathParam("dataId") int id, TurData turData) throws Exception {
		TurData turDataEdit = this.turDataRepository.getOne(id);
		turDataEdit.setName(turData.getName());
		turDataEdit.setType(turData.getType());
		this.turDataRepository.save(turDataEdit);
		return turDataEdit;
	}

	@Path("/{dataId}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("dataId") int id) throws Exception {
		this.turDataRepository.delete(id);
		return true;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurData add(TurData turData) throws Exception {
		this.turDataRepository.save(turData);
		return turData;

	}

	@POST
	@Path("import")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	public Response importData(@DefaultValue("true") @FormDataParam("enabled") boolean enabled,
			@FormDataParam("file") InputStream inputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail, @Context UriInfo uriInfo)
			throws JSONException, IOException, SAXException, TikaException {

		BodyContentHandler handler = new BodyContentHandler(-1);
		Metadata metadata = new Metadata();

		ParseContext pcontext = new ParseContext();

		// parsing the document using PDF parser
		PDFParser pdfparser = new PDFParser();
		pdfparser.parse(inputStream, handler, metadata, pcontext);


		String sentences[] = TurOpenNLPConnector.sentenceDetect(handler.toString());

		TurData turData = new TurData();

		turData.setName(fileDetail.getFileName());
		turData.setType(FilenameUtils.getExtension(fileDetail.getFileName()));
		this.turDataRepository.save(turData);

		for (String sentence : sentences) {
			TurDataGroupSentence turDataGroupSentence = new TurDataGroupSentence();
			turDataGroupSentence.setTurData(turData);
			turDataGroupSentence.setSentence(sentence);
			this.turDataGroupSentenceRepository.save(turDataGroupSentence);
		}
		
		JSONObject jsonTraining = new JSONObject();
		jsonTraining.put("sentences", sentences);
		return Response.status(200).entity(jsonTraining.toString()).build();
	}
}
