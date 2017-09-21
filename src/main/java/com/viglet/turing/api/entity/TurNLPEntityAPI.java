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

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;

@Component
@Path("entity")
public class TurNLPEntityAPI {
	@Autowired
	private TurNLPEntityRepository turNLPEntityRepository;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurNLPEntity> list() throws JSONException {
		return this.turNLPEntityRepository.findAll();
	}

	@Path("{entityId}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurNLPEntity detail(@PathParam("entityId") int id) throws JSONException {
		return this.turNLPEntityRepository.findById(id);
	}

	@Path("/{entityId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurNLPEntity update(@PathParam("entityId") int id, TurNLPEntity turNLPEntity) throws Exception {
		TurNLPEntity turNLPEntityEdit =  this.turNLPEntityRepository.findById(id);
		turNLPEntityEdit.setName(turNLPEntity.getName());
		turNLPEntityEdit.setDescription(turNLPEntity.getDescription());
		this.turNLPEntityRepository.save(turNLPEntityEdit);
		return turNLPEntityEdit;
	}

	@Path("{id}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("id") int id) {
		TurNLPEntity turNLPEntity =  this.turNLPEntityRepository.findById(id);
		this.turNLPEntityRepository.delete(turNLPEntity);
		return true;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurNLPEntity add(TurNLPEntity turNLPEntity) throws Exception {
		this.turNLPEntityRepository.save(turNLPEntity);
		return turNLPEntity;

	}
}