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

import com.viglet.turing.persistence.model.ml.TurMLCategory;
import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupCategory;
import com.viglet.turing.persistence.repository.storage.TurDataGroupCategoryRepository;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;

@Component
@Path("ml/data/group/{dataGroupId}/category")
public class TurMLDataGroupCategoryAPI {

	@Autowired
	TurDataGroupRepository turDataGroupRepository;
	@Autowired
	TurDataGroupCategoryRepository turDataGroupCategoryRepository;

	@GET
	@Produces("application/json")
	public List<TurDataGroupCategory> list(@PathParam("dataGroupId") int dataGroupId) throws JSONException {
		TurDataGroup turDataGroup = turDataGroupRepository.getOne(dataGroupId);
		return this.turDataGroupCategoryRepository.findByTurDataGroup(turDataGroup);
	}

	@Path("{dataGroupCategoryId}")
	@GET
	@Produces("application/json")
	public TurDataGroupCategory mlSolution(@PathParam("dataGroupId") int dataGroupId,
			@PathParam("dataGroupCategoryId") int id) throws JSONException {
		return this.turDataGroupCategoryRepository.getOne(id);
	}

	@Path("/{dataGroupCategoryId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroupCategory update(@PathParam("dataGroupId") int dataGroupId,
			@PathParam("dataGroupCategoryId") int id, TurMLCategory turMLCategory) throws Exception {
		TurDataGroupCategory turDataGroupCategoryEdit = this.turDataGroupCategoryRepository.getOne(id);
		turDataGroupCategoryEdit.setTurMLCategory(turMLCategory);
		this.turDataGroupCategoryRepository.save(turDataGroupCategoryEdit);
		return turDataGroupCategoryEdit;
	}

	@Path("{dataGroupCategoryId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("dataGroupId") int dataGroupId, @PathParam("dataGroupCategoryId") int id) {
		this.turDataGroupCategoryRepository.delete(id);
		return true;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurDataGroupCategory add(@PathParam("dataGroupId") int dataGroupId,
			TurDataGroupCategory turDataGroupCategory) throws Exception {
		TurDataGroup turDataGroup = turDataGroupRepository.getOne(dataGroupId);
		turDataGroupCategory.setTurDataGroup(turDataGroup);
		this.turDataGroupCategoryRepository.save(turDataGroupCategory);
		return turDataGroupCategory;

	}
}
