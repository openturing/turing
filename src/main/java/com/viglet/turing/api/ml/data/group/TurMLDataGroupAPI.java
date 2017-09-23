package com.viglet.turing.api.ml.data.group;

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

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;

@Component
@Path("ml/data/group")
public class TurMLDataGroupAPI {

	@Autowired
	TurDataGroupRepository turDataGroupRepository;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurDataGroup> list() throws JSONException {
		return this.turDataGroupRepository.findAll();
	}

	@GET
	@Path("/{dataGroupId}")
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroup dataGroup(@PathParam("dataGroupId") int id) throws JSONException {
		return this.turDataGroupRepository.findById(id);
	}

	@Path("/{dataGroupId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroup update(@PathParam("dataGroupId") int id, TurDataGroup turDataGroup) throws Exception {
		TurDataGroup turDataGroupEdit = this.turDataGroupRepository.findById(id);
		turDataGroupEdit.setName(turDataGroup.getName());
		turDataGroupEdit.setDescription(turDataGroup.getDescription());
		this.turDataGroupRepository.save(turDataGroupEdit);
		return turDataGroupEdit;
	}

	@Path("/{dataGroupId}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("dataGroupId") int id) throws Exception {
		this.turDataGroupRepository.delete(id);
		return true;

	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurDataGroup add(TurDataGroup turDataGroup) throws Exception {
		this.turDataGroupRepository.save(turDataGroup);
		return turDataGroup;

	}
}
