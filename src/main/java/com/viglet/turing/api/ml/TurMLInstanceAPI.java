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

import com.viglet.turing.persistence.model.ml.TurMLInstance;
import com.viglet.turing.persistence.service.ml.TurMLInstanceService;

@Path("/ml")
public class TurMLInstanceAPI {

	TurMLInstanceService turMLInstanceService = new TurMLInstanceService();

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurMLInstance> list() throws JSONException {
		return turMLInstanceService.listAll();
	}

	@Path("{mlInstanceId}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurMLInstance detailService(@PathParam("mlInstanceId") int id) throws JSONException {
		return turMLInstanceService.get(id);
	}

	@Path("/{mlInstanceId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurMLInstance update(@PathParam("mlInstanceId") int id, TurMLInstance turMLInstance) throws Exception {
		TurMLInstance turMLInstanceEdit = turMLInstanceService.get(id);
		turMLInstanceEdit.setTitle(turMLInstance.getTitle());
		turMLInstanceEdit.setDescription(turMLInstance.getDescription());
		turMLInstanceEdit.setTurMLVendor(turMLInstance.getTurMLVendor());
		turMLInstanceEdit.setHost(turMLInstance.getHost());
		turMLInstanceEdit.setPort(turMLInstance.getPort());
		turMLInstanceEdit.setEnabled(turMLInstance.getEnabled());
		turMLInstanceService.save(turMLInstanceEdit);
		return turMLInstanceEdit;
	}

	@Path("{mlInstanceId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("mlInstanceId") int id) {
		return turMLInstanceService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurMLInstance add(TurMLInstance turMLInstance) throws Exception {
		turMLInstanceService.save(turMLInstance);
		return turMLInstance;

	}
}