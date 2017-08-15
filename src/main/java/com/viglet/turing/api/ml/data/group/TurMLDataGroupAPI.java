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


import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.service.storage.TurDataGroupService;

@Path("/ml/data/group")
public class TurMLDataGroupAPI {
	TurDataGroupService turDataGroupService = new TurDataGroupService();
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurDataGroup> list() throws JSONException {
		 return turDataGroupService.listAll();
	}

	@GET
	@Path("{dataGroupId}")
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroup dataGroup(@PathParam("dataGroupId") int id) throws JSONException {
		 return turDataGroupService.get(id);
	}
	
	@Path("/{dataGroupId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroup update(@PathParam("dataGroupId") int id, TurDataGroup turDataGroup) throws Exception {
		TurDataGroup turDataGroupEdit = turDataGroupService.get(id);
		turDataGroupEdit.setName(turDataGroup.getName());
		turDataGroupEdit.setDescription(turDataGroup.getDescription());
		turDataGroupService.save(turDataGroupEdit);
		return turDataGroupEdit;
	}

	@Path("/{dataGroupId}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("dataGroupId") int id) throws Exception {
		return turDataGroupService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurDataGroup add(TurDataGroup turDataGroup) throws Exception {
		turDataGroupService.save(turDataGroup);
		return turDataGroup;

	}
}
