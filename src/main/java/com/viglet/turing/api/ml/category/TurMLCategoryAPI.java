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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.ml.TurMLCategory;
import com.viglet.turing.persistence.repository.ml.TurMLCategoryRepository;

@Component
@Path("ml/category")
public class TurMLCategoryAPI {

	@Autowired
	TurMLCategoryRepository turMLCategoryRepository;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurMLCategory> list() throws JSONException {
		return this.turMLCategoryRepository.findAll();
	}

	@Path("{mlCategoryId}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurMLCategory mlSolution(@PathParam("mlCategoryId") int id) throws JSONException {
		return this.turMLCategoryRepository.findById(id);
	}

	@Path("/{mlCategoryId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurMLCategory update(@PathParam("mlCategoryId") int id, TurMLCategory turMLCategory) throws Exception {
		TurMLCategory turMLCategoryEdit = this.turMLCategoryRepository.findById(id);
		turMLCategoryEdit.setInternalName(turMLCategory.getInternalName());
		turMLCategoryEdit.setName(turMLCategory.getName());
		turMLCategoryEdit.setDescription(turMLCategory.getDescription());
		this.turMLCategoryRepository.save(turMLCategoryEdit);
		this.turMLCategoryRepository.flush();

		return turMLCategoryEdit;
	}

	@Path("{mlCategoryId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("mlCategoryId") int id) {
		this.turMLCategoryRepository.delete(id);
		return true;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurMLCategory add(TurMLCategory turMLCategory) throws Exception {
		this.turMLCategoryRepository.save(turMLCategory);
		return turMLCategory;

	}
}
