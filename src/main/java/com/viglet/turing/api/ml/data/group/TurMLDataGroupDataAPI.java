package com.viglet.turing.api.ml.data.group;

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
import org.xml.sax.SAXException;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;
import com.viglet.turing.persistence.model.storage.TurData;
import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupData;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.service.storage.TurDataGroupDataService;
import com.viglet.turing.persistence.service.storage.TurDataGroupSentenceService;
import com.viglet.turing.persistence.service.storage.TurDataGroupService;
import com.viglet.turing.persistence.service.storage.TurDataService;
import com.viglet.turing.plugins.opennlp.TurOpenNLPConnector;

@Path("/ml/data/group/{dataGroupId}/data")
public class TurMLDataGroupDataAPI {
	TurDataGroupService turDataGroupService = new TurDataGroupService();
	TurDataGroupDataService turDataGroupDataService = new TurDataGroupDataService();
	TurDataService turDataService = new TurDataService();
	TurDataGroupSentenceService turDataGroupSentenceService = new TurDataGroupSentenceService();
	
	@GET
	@Produces("application/json")
	public List<TurDataGroupData> list(@PathParam("dataGroupId") int dataGroupId) throws JSONException {
		TurDataGroup turDataGroup = turDataGroupService.get(dataGroupId);
		return turDataGroupDataService.findByDataGroup(turDataGroup);
	}

	@Path("{dataGroupDataId}")
	@GET
	@Produces("application/json")
	public TurDataGroupData mlSolution(@PathParam("dataGroupId") int dataGroupId, @PathParam("dataGroupDataId") int id)
			throws JSONException {
		return turDataGroupDataService.get(id);
	}

	@Path("/{dataGroupDataId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroupData update(@PathParam("dataGroupId") int dataGroupId, @PathParam("dataGroupDataId") int id,
			TurData turMLData) throws Exception {
		TurDataGroupData turDataGroupDataEdit = turDataGroupDataService.get(id);
		turDataGroupDataEdit.setTurData(turMLData);
		turDataGroupDataService.save(turDataGroupDataEdit);
		return turDataGroupDataEdit;
	}

	@Path("{dataGroupDataId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("dataGroupId") int dataGroupId, @PathParam("dataGroupDataId") int id) {
		return turDataGroupDataService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurDataGroupData add(@PathParam("dataGroupId") int dataGroupId, TurDataGroupData turDataGroupData)
			throws Exception {
		TurDataGroup turDataGroup = turDataGroupService.get(dataGroupId);
		turDataGroupData.setTurDataGroup(turDataGroup);
		turDataGroupDataService.save(turDataGroupData);
		return turDataGroupData;

	}
	
	@POST
	@Path("import")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroupData importData(@PathParam("dataGroupId") int dataGroupId, @DefaultValue("true") @FormDataParam("enabled") boolean enabled,
			@FormDataParam("file") InputStream inputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail, @Context UriInfo uriInfo)
			throws JSONException, IOException, SAXException, TikaException {
		
		TurDataGroup turDataGroup = turDataGroupService.get(dataGroupId);
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
		turDataService.save(turData);

		for (String sentence : sentences) {
			TurDataGroupSentence turDataGroupSentence = new TurDataGroupSentence();
			turDataGroupSentence.setTurData(turData);
			turDataGroupSentence.setSentence(sentence);
			turDataGroupSentenceService.save(turDataGroupSentence);
		}
		
		TurDataGroupData turDataGroupData = new TurDataGroupData();
		
		turDataGroupData.setTurData(turData);
		turDataGroupData.setTurDataGroup(turDataGroup);
		turDataGroupDataService.save(turDataGroupData);

		return turDataGroupData;
	}
}
