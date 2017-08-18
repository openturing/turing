package com.viglet.turing.api.sn;

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

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.service.sn.TurSNSiteService;

@Path("/sn")
public class TurSNSiteAPI {
	TurSNSiteService turSNSiteService = new TurSNSiteService();

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurSNSite> list() throws JSONException {
		 return turSNSiteService.listAll();
	}

	@GET
	@Path("{snSiteId}")
	@Produces(MediaType.APPLICATION_JSON)
	public TurSNSite dataGroup(@PathParam("snSiteId") int id) throws JSONException {
		 return turSNSiteService.get(id);
	}
	
	@Path("/{snSiteId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurSNSite update(@PathParam("snSiteId") int id, TurSNSite turSNSite) throws Exception {
		TurSNSite turSNSiteEdit = turSNSiteService.get(id);
		turSNSiteEdit.setName(turSNSite.getName());
		turSNSiteEdit.setDescription(turSNSite.getDescription());
		turSNSiteEdit.setLanguage(turSNSite.getLanguage());
		turSNSiteEdit.setTurSEInstance(turSNSite.getTurSEInstance());
		turSNSiteEdit.setTurNLPInstance(turSNSite.getTurNLPInstance());
		turSNSiteService.save(turSNSiteEdit);
		return turSNSiteEdit;
	}

	@Path("/{snSiteId}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("snSiteId") int id) throws Exception {
		return turSNSiteService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurSNSite add(TurSNSite turSNSite) throws Exception {
		turSNSiteService.save(turSNSite);
		return turSNSite;

	}
		
}