package com.viglet.turing.api.se;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONException;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.service.se.TurSEInstanceService;
import com.viglet.turing.solr.TurSolr;

@Path("/se")
public class TurSEInstanceAPI {
	TurSEInstanceService turSEInstanceService = new TurSEInstanceService();

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurSEInstance> list() throws JSONException {
		 return turSEInstanceService.listAll();
	}

	@GET
	@Path("{seInstanceId}")
	@Produces(MediaType.APPLICATION_JSON)
	public TurSEInstance dataGroup(@PathParam("seInstanceId") int id) throws JSONException {
		 return turSEInstanceService.get(id);
	}
	
	@Path("/{seInstanceId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurSEInstance update(@PathParam("seInstanceId") int id, TurSEInstance turSEInstance) throws Exception {
		TurSEInstance turSEInstanceEdit = turSEInstanceService.get(id);
		turSEInstanceEdit.setTitle(turSEInstance.getTitle());
		turSEInstanceEdit.setDescription(turSEInstance.getDescription());
		turSEInstanceEdit.setTurSEVendor(turSEInstance.getTurSEVendor());
		turSEInstanceEdit.setHost(turSEInstance.getHost());
		turSEInstanceEdit.setPort(turSEInstance.getPort());
		turSEInstanceEdit.setEnabled(turSEInstance.getEnabled());
		turSEInstanceEdit.setSelected(turSEInstance.getSelected());
		turSEInstanceService.save(turSEInstanceEdit);
		return turSEInstanceEdit;
	}

	@Path("/{seInstanceId}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("seInstanceId") int id) throws Exception {
		return turSEInstanceService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurSEInstance add(TurSEInstance turSEInstance) throws Exception {
		turSEInstanceService.save(turSEInstance);
		return turSEInstance;

	}
	
	@GET
	@Path("select")
	@Produces(MediaType.APPLICATION_JSON)
	public Response select(@QueryParam("q") String q, @QueryParam("p") int p, @QueryParam("fq[]") List<String> fq)
			throws JSONException {
		String result = null;
		TurSolr turSolr = new TurSolr();
		try {
			result = turSolr.retrieveSolr(q, fq, p).toString();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return Response.status(200).entity(result).build();
	}
	
	
}