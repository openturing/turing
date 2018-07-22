package com.viglet.turing.api.ml;

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

import com.viglet.turing.persistence.model.ml.TurMLVendor;
import com.viglet.turing.persistence.repository.ml.TurMLVendorRepository;

@Component
@Path("ml/vendor")
public class TurMLVendorAPI {

	@Autowired
	TurMLVendorRepository turMLVendorRepository;

	@GET
	@Produces("application/json")
	public List<TurMLVendor> list() throws JSONException {
		return this.turMLVendorRepository.findAll();
	}

	@Path("{mlVendorId}")
	@GET
	@Produces("application/json")
	public TurMLVendor mlSolution(@PathParam("mlVendorId") String id) throws JSONException {
		return this.turMLVendorRepository.findById(id).get();
	}

	@Path("/{mlVendorId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurMLVendor update(@PathParam("mlVendorId") String id, TurMLVendor turMLVendor) throws Exception {
		TurMLVendor turMLVendorEdit = this.turMLVendorRepository.findById(id).get();
		turMLVendorEdit.setDescription(turMLVendor.getDescription());
		turMLVendorEdit.setPlugin(turMLVendor.getPlugin());
		turMLVendorEdit.setTitle(turMLVendor.getTitle());
		turMLVendorEdit.setWebsite(turMLVendor.getWebsite());
		this.turMLVendorRepository.save(turMLVendorEdit);
		return turMLVendorEdit;
	}

	@Path("{mlVendorId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("mlVendorId") String id) {
		this.turMLVendorRepository.delete(id);
		return true;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurMLVendor add(TurMLVendor turMLVendor) throws Exception {
		this.turMLVendorRepository.save(turMLVendor);
		return turMLVendor;

	}
}
