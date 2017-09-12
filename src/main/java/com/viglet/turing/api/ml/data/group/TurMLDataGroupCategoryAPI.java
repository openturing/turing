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

import com.viglet.turing.persistence.model.ml.TurMLCategory;
import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupCategory;
import com.viglet.turing.persistence.service.ml.TurMLCategoryService;
import com.viglet.turing.persistence.service.storage.TurDataGroupCategoryService;
import com.viglet.turing.persistence.service.storage.TurDataGroupService;

@Path("/ml/data/group/{dataGroupId}/category")
public class TurMLDataGroupCategoryAPI {
	TurDataGroupService turDataGroupService = new TurDataGroupService();
	TurDataGroupCategoryService turDataGroupCategoryService = new TurDataGroupCategoryService();

	@GET
	@Produces("application/json")
	public List<TurDataGroupCategory> list(@PathParam("dataGroupId") int dataGroupId) throws JSONException {
		TurDataGroup turDataGroup = turDataGroupService.get(dataGroupId);
		return turDataGroupCategoryService.findByDataGroup(turDataGroup);
	}

	@Path("{dataGroupCategoryId}")
	@GET
	@Produces("application/json")
	public TurDataGroupCategory mlSolution(@PathParam("dataGroupId") int dataGroupId, @PathParam("dataGroupCategoryId") int id)
			throws JSONException {
		return turDataGroupCategoryService.get(id);
	}

	@Path("/{dataGroupCategoryId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroupCategory update(@PathParam("dataGroupId") int dataGroupId, @PathParam("dataGroupCategoryId") int id,
			TurDataGroupCategory turDataGroupCategory) throws Exception {
		TurDataGroupCategory turDataGroupCategoryEdit = turDataGroupCategoryService.get(id);
		turDataGroupCategoryEdit.setTurDataGroup(turDataGroupCategory.getTurDataGroup());
		turDataGroupCategoryEdit.setTurMLCategory(turDataGroupCategory.getTurMLCategory());
		turDataGroupCategoryService.save(turDataGroupCategoryEdit);
		return turDataGroupCategoryEdit;
	}

	@Path("{dataGroupCategoryId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("dataGroupId") int dataGroupId, @PathParam("dataGroupCategoryId") int id) {
		return turDataGroupCategoryService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurDataGroupCategory add(@PathParam("dataGroupId") int dataGroupId, TurDataGroupCategory turDataGroupCategory) throws Exception {
		turDataGroupCategoryService.save(turDataGroupCategory);
		return turDataGroupCategory;

	}
}
