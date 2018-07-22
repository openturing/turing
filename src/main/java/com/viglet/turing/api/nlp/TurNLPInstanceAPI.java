package com.viglet.turing.api.nlp;

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

import org.json.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;

import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;

@ComponentScan
@Path("nlp")
public class TurNLPInstanceAPI {

	@Autowired
	TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	TurNLP turNLP;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurNLPInstance> list() throws JSONException {
		return this.turNLPInstanceRepository.findAll();
	}

	@GET
	@Path("/{nlpInstanceId}")
	@Produces(MediaType.APPLICATION_JSON)
	public TurNLPInstance dataGroup(@PathParam("nlpInstanceId") int id) throws JSONException {
		return this.turNLPInstanceRepository.findById(id);
	}

	@Path("/{nlpInstanceId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurNLPInstance update(@PathParam("nlpInstanceId") int id, TurNLPInstance turNLPInstance) throws Exception {
		TurNLPInstance turNLPInstanceEdit = turNLPInstanceRepository.findById(id);
		turNLPInstanceEdit.setTitle(turNLPInstance.getTitle());
		turNLPInstanceEdit.setDescription(turNLPInstance.getDescription());
		turNLPInstanceEdit.setTurNLPVendor(turNLPInstance.getTurNLPVendor());
		turNLPInstanceEdit.setHost(turNLPInstance.getHost());
		turNLPInstanceEdit.setPort(turNLPInstance.getPort());
		turNLPInstanceEdit.setEnabled(turNLPInstance.getEnabled());
		turNLPInstanceEdit.setLanguage(turNLPInstance.getLanguage());
		this.turNLPInstanceRepository.save(turNLPInstanceEdit);
		return turNLPInstanceEdit;
	}

	@Path("/{nlpInstanceId}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("nlpInstanceId") int id) throws Exception {
		this.turNLPInstanceRepository.delete(id);
		return true;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurNLPInstance add(TurNLPInstance turNLPInstance) throws Exception {
		this.turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);
		return turNLPInstance;

	}

	@POST
	@Path("/{nlpInstanceId}/validate")
	@Produces(MediaType.APPLICATION_JSON)
	public Response validate(@PathParam("nlpInstanceId") int id, TurNLPTextValidate textValidate) throws JSONException {

		turNLP.startup(this.turNLPInstanceRepository.findById(id), textValidate.getText());
		return Response.status(200).entity(turNLP.validate()).build();
	}

	public boolean isNumeric(String str) {
		return str.matches("-?\\d+(\\.\\d+)?"); // match a number with optional
												// '-' and decimal.
	}

	public static class TurNLPTextValidate {
		String text;

		public TurNLPTextValidate() {
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}
	}
}