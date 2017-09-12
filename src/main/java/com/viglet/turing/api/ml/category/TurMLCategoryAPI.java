package com.viglet.turing.api.ml.category;

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
import com.viglet.turing.persistence.service.ml.TurMLCategoryService;

@Path("/ml/category")
public class TurMLCategoryAPI {
	TurMLCategoryService turMLCategoryService = new TurMLCategoryService();

	@GET
	@Produces("application/json")
	public List<TurMLCategory> list(int dataGroupId) throws JSONException {
		return turMLCategoryService.listAll();
	}

	@Path("{mlCategoryId}")
	@GET
	@Produces("application/json")
	public TurMLCategory mlSolution(@PathParam("mlCategoryId") String id) throws JSONException {
		return turMLCategoryService.get(id);
	}

	@Path("/{mlCategoryId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurMLCategory update(@PathParam("mlCategoryId") String id, TurMLCategory turMLCategory) throws Exception {
		TurMLCategory turMLCategoryEdit = turMLCategoryService.get(id);
		turMLCategoryEdit.setInternalName(turMLCategory.getInternalName());
		turMLCategoryEdit.setName(turMLCategory.getName());
		turMLCategoryEdit.setDescription(turMLCategory.getDescription());
		turMLCategoryService.save(turMLCategoryEdit);
		return turMLCategoryEdit;
	}

	@Path("{mlCategoryId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("mlCategoryId") int id) {
		return turMLCategoryService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurMLCategory add(TurMLCategory turMLCategory) throws Exception {
		turMLCategoryService.save(turMLCategory);
		return turMLCategory;

	}
}
