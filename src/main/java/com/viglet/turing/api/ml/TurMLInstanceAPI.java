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

import com.viglet.turing.persistence.model.ml.TurMLInstance;
import com.viglet.turing.persistence.repository.ml.TurMLInstanceRepository;

@Component
@Path("ml")
public class TurMLInstanceAPI {

	@Autowired
	TurMLInstanceRepository turMLInstanceRepository;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurMLInstance> list() throws JSONException {
		return this.turMLInstanceRepository.findAll();
	}

	@Path("{mlInstanceId}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurMLInstance detailService(@PathParam("mlInstanceId") int id) throws JSONException {
		return this.turMLInstanceRepository.findById(id);
	}

	@Path("/{mlInstanceId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurMLInstance update(@PathParam("mlInstanceId") int id, TurMLInstance turMLInstance) throws Exception {
		TurMLInstance turMLInstanceEdit = this.turMLInstanceRepository.findById(id);
		turMLInstanceEdit.setTitle(turMLInstance.getTitle());
		turMLInstanceEdit.setDescription(turMLInstance.getDescription());
		turMLInstanceEdit.setTurMLVendor(turMLInstance.getTurMLVendor());
		turMLInstanceEdit.setHost(turMLInstance.getHost());
		turMLInstanceEdit.setPort(turMLInstance.getPort());
		turMLInstanceEdit.setEnabled(turMLInstance.getEnabled());
		this.turMLInstanceRepository.save(turMLInstanceEdit);
		return turMLInstanceEdit;
	}

	@Path("{mlInstanceId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("mlInstanceId") int id) {
		this.turMLInstanceRepository.delete(id);
		return true;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurMLInstance add(TurMLInstance turMLInstance) throws Exception {
		this.turMLInstanceRepository.save(turMLInstance);
		return turMLInstance;

	}
}