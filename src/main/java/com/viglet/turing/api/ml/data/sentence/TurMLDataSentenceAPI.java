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

import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.service.storage.TurDataGroupSentenceService;

@Path("/ml/data/sentence")
public class TurMLDataSentenceAPI {
	TurDataGroupSentenceService turDataGroupSentenceService = new TurDataGroupSentenceService();

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurDataGroupSentence> list() throws JSONException {
		return turDataGroupSentenceService.listAll();
	}

	@Path("{sentenceId}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroupSentence detail(@PathParam("sentenceId") int id) throws JSONException {
		return turDataGroupSentenceService.get(id);
	}

	@Path("/{sentenceId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroupSentence update(@PathParam("sentenceId") int id, TurDataGroupSentence turDataSentence) throws Exception {
		TurDataGroupSentence turDataGroupSentenceEdit = turDataGroupSentenceService.get(id);
		turDataGroupSentenceEdit.setSentence(turDataSentence.getSentence());
		turDataGroupSentenceEdit.setTurData(turDataSentence.getTurData());
		turDataGroupSentenceEdit.setTurMLCategory(turDataSentence.getTurMLCategory());
		turDataGroupSentenceService.save(turDataGroupSentenceEdit);
		return turDataGroupSentenceEdit;
	}

	@Path("/{sentenceId}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("sentenceId") int id) throws Exception {
		return turDataGroupSentenceService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurDataGroupSentence add(TurDataGroupSentence turDataSentence) throws Exception {
		turDataGroupSentenceService.save(turDataSentence);
		return turDataSentence;

	}
}
