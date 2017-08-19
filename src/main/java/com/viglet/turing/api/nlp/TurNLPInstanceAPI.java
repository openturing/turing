package com.viglet.turing.api.nlp;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONException;

import com.viglet.turing.nlp.TurNLP;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.service.nlp.TurNLPInstanceService;
import com.viglet.turing.solr.TurSolr;

@Path("/nlp")
public class TurNLPInstanceAPI {
	TurNLPInstanceService turNLPInstanceService = new TurNLPInstanceService();

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurNLPInstance> list() throws JSONException {
		return turNLPInstanceService.listAll();
	}

	@GET
	@Path("{nlpInstanceId}")
	@Produces(MediaType.APPLICATION_JSON)
	public TurNLPInstance dataGroup(@PathParam("nlpInstanceId") int id) throws JSONException {
		return turNLPInstanceService.get(id);
	}

	@Path("/{nlpInstanceId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurNLPInstance update(@PathParam("nlpInstanceId") int id, TurNLPInstance turNLPInstance) throws Exception {
		TurNLPInstance turNLPInstanceEdit = turNLPInstanceService.get(id);
		turNLPInstanceEdit.setTitle(turNLPInstance.getTitle());
		turNLPInstanceEdit.setDescription(turNLPInstance.getDescription());
		turNLPInstanceEdit.setTurNLPVendor(turNLPInstance.getTurNLPVendor());
		turNLPInstanceEdit.setHost(turNLPInstance.getHost());
		turNLPInstanceEdit.setPort(turNLPInstance.getPort());
		turNLPInstanceEdit.setEnabled(turNLPInstance.getEnabled());
		turNLPInstanceService.save(turNLPInstanceEdit);
		return turNLPInstanceEdit;
	}

	@Path("/{nlpInstanceId}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("nlpInstanceId") int id) throws Exception {
		return turNLPInstanceService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurNLPInstance add(TurNLPInstance turNLPInstance) throws Exception {
		turNLPInstanceService.save(turNLPInstance);
		return turNLPInstance;

	}

	@POST
	@Path("{nlpInstanceId}/validate")
	@Produces(MediaType.APPLICATION_JSON)
	public Response update(@PathParam("nlpInstanceId") int id, TurNLPTextValidate textValidate) throws JSONException {

		TurNLP turNLP = new TurNLP(id, textValidate.getText());

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