package com.viglet.turing.api.ml.data.group;

import java.util.List;

import javax.ws.rs.GET;
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
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroup dataGroup(@PathParam("id") int id) throws JSONException {
		 return turDataGroupService.get(id);
	}
}
