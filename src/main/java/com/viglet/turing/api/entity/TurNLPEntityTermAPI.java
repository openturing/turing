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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.nlp.term.TurTerm;
import com.viglet.turing.persistence.repository.nlp.term.TurTermRepository;

@Component
@Path("entity/terms")
public class TurNLPEntityTermAPI {
	@Autowired
	private TurTermRepository turTermRepository;
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurTerm> list() throws JSONException {
		return this.turTermRepository.findAll();
	}

	@Path("{termId}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurTerm detail(@PathParam("termId") int id) throws JSONException {
		return this.turTermRepository.getOne(id);
	}

	@Path("/{termId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurTerm update(@PathParam("termId") int id, TurTerm turTerm) throws Exception {
		TurTerm turTermEdit = this.turTermRepository.getOne(id);
		turTermEdit.setName(turTerm.getName());
		turTermEdit.setIdCustom(turTerm.getIdCustom());
		this.turTermRepository.save(turTermEdit);
		return turTermEdit;
	}

	@Path("{termId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("id") int id) {
		this.turTermRepository.delete(id);
		return true;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurTerm add(TurTerm turTerm) throws Exception {
		this.turTermRepository.save(turTerm);
		return turTerm;

	}
}
