package com.viglet.turing.api.entity;

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

import com.viglet.turing.persistence.model.nlp.term.TurTerm;
import com.viglet.turing.persistence.service.nlp.term.TurTermService;

@Path("/entity/terms")
public class TurNLPEntityTermAPI {
	TurTermService turTermService = new TurTermService();
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurTerm> list() throws JSONException {
		return turTermService.listAll();
	}

	@Path("{termId}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurTerm detail(@PathParam("termId") int id) throws JSONException {
		return turTermService.get(id);
	}

	@Path("/{termId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurTerm update(@PathParam("termId") int id, TurTerm turTerm) throws Exception {
		TurTerm turTermEdit = turTermService.get(id);
		turTermEdit.setName(turTerm.getName());
		turTermEdit.setIdCustom(turTerm.getIdCustom());
		turTermService.save(turTermEdit);
		return turTermEdit;
	}

	@Path("{termId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("id") int id) {
		return turTermService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurTerm add(TurTerm turTerm) throws Exception {
		turTermService.save(turTerm);
		return turTerm;

	}
}
