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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.repository.storage.TurDataGroupSentenceRepository;

@Component
@Path("ml/data/sentence")
public class TurMLDataSentenceAPI {

	@Autowired
	TurDataGroupSentenceRepository turDataGroupSentenceRepository;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurDataGroupSentence> list() throws JSONException {
		return this.turDataGroupSentenceRepository.findAll();
	}

	@Path("{sentenceId}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroupSentence detail(@PathParam("sentenceId") int id) throws JSONException {
		return turDataGroupSentenceRepository.findOne(id);
	}

	@Path("/{sentenceId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroupSentence update(@PathParam("sentenceId") int id, TurDataGroupSentence turDataSentence)
			throws Exception {
		TurDataGroupSentence turDataGroupSentenceEdit = turDataGroupSentenceRepository.findOne(id);
		turDataGroupSentenceEdit.setSentence(turDataSentence.getSentence());
		turDataGroupSentenceEdit.setTurData(turDataSentence.getTurData());
		turDataGroupSentenceEdit.setTurMLCategory(turDataSentence.getTurMLCategory());
		this.turDataGroupSentenceRepository.save(turDataGroupSentenceEdit);
		return turDataGroupSentenceEdit;
	}

	@Path("/{sentenceId}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("sentenceId") int id) throws Exception {
		this.turDataGroupSentenceRepository.delete(id);
		return true;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurDataGroupSentence add(TurDataGroupSentence turDataSentence) throws Exception {
		this.turDataGroupSentenceRepository.save(turDataSentence);
		return turDataSentence;

	}
}
