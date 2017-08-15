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

import com.viglet.turing.persistence.service.nlp.TurNLPEntityService;
import com.viglet.turing.persistence.model.nlp.TurNLPEntity;

@Path("/entity")
public class TurNLPEntityAPI {
	TurNLPEntityService turNLPEntityService = new TurNLPEntityService();

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurNLPEntity> list() throws JSONException {
		return turNLPEntityService.listAll();
	}

	@Path("{entityId}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurNLPEntity detail(@PathParam("entityId") int id) throws JSONException {
		return turNLPEntityService.get(id);
	}

	@Path("/{entityId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurNLPEntity update(@PathParam("entityId") int id, TurNLPEntity turNLPEntity) throws Exception {
		TurNLPEntity turNLPEntityEdit = turNLPEntityService.get(id);
		turNLPEntityEdit.setName(turNLPEntity.getName());
		turNLPEntityEdit.setDescription(turNLPEntity.getDescription());
		turNLPEntityService.save(turNLPEntityEdit);
		return turNLPEntityEdit;
	}

	@Path("{id}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("id") int id) {
		return turNLPEntityService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurNLPEntity add(TurNLPEntity turNLPEntity) throws Exception {
		turNLPEntityService.save(turNLPEntity);
		return turNLPEntity;

	}
}