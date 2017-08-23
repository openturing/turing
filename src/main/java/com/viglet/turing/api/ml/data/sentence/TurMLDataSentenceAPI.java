package com.viglet.turing.api.ml.data.sentence;

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


import org.json.JSONException;

import com.viglet.turing.persistence.model.storage.TurDataSentence;
import com.viglet.turing.persistence.service.storage.TurDataSentenceService;

@Path("/ml/data/sentence")
public class TurMLDataSentenceAPI {
	TurDataSentenceService turDataSentenceService = new TurDataSentenceService();

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurDataSentence> list() throws JSONException {
		return turDataSentenceService.listAll();
	}

	@Path("{sentenceId}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataSentence detail(@PathParam("sentenceId") int id) throws JSONException {
		return turDataSentenceService.get(id);
	}

	@Path("/{sentenceId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataSentence update(@PathParam("sentenceId") int id, TurDataSentence turDataSentence) throws Exception {
		TurDataSentence turDataSentenceEdit = turDataSentenceService.get(id);
		turDataSentenceEdit.setSentence(turDataSentence.getSentence());
		turDataSentenceEdit.setTurData(turDataSentence.getTurData());
		turDataSentenceEdit.setTurMLCategory(turDataSentence.getTurMLCategory());
		turDataSentenceService.save(turDataSentenceEdit);
		return turDataSentenceEdit;
	}

	@Path("/{sentenceId}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("sentenceId") int id) throws Exception {
		return turDataSentenceService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurDataSentence add(TurDataSentence turDataSentence) throws Exception {
		turDataSentenceService.save(turDataSentence);
		return turDataSentence;

	}
}
