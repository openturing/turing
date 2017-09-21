package com.viglet.turing.api.nlp;

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

import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorRepository;

@Component
@Path("nlp/vendor")
public class TurNLPVendorAPI {
	
	@Autowired
	TurNLPVendorRepository turNLPVendorRepository;
	
	@GET
	@Produces("application/json")
	public List<TurNLPVendor> list() throws JSONException {
		return this.turNLPVendorRepository.findAll();
	}

	@Path("{nlpVendorId}")
	@GET
	@Produces("application/json")
	public TurNLPVendor nlpSolution(@PathParam("nlpVendorId") String id) throws JSONException {
		return this.turNLPVendorRepository.findOne(id);
	}
	

	@Path("/{nlpVendorId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurNLPVendor update(@PathParam("nlpVendorId") String id, TurNLPVendor turNLPVendor) throws Exception {
		TurNLPVendor turNLPVendorEdit = this.turNLPVendorRepository.findOne(id);
		turNLPVendorEdit.setDescription(turNLPVendor.getDescription());
		turNLPVendorEdit.setPlugin(turNLPVendor.getPlugin());
		turNLPVendorEdit.setTitle(turNLPVendor.getTitle());
		turNLPVendorEdit.setWebsite(turNLPVendor.getWebsite());		
		this.turNLPVendorRepository.save(turNLPVendorEdit);
		return turNLPVendorEdit;
	}

	@Path("{nlpVendorId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("nlpVendorId") String id) {
		this.turNLPVendorRepository.delete(id);
		return true;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurNLPVendor add(TurNLPVendor turNLPVendor) throws Exception {
		this.turNLPVendorRepository.save(turNLPVendor);
		return turNLPVendor;

	}
}
