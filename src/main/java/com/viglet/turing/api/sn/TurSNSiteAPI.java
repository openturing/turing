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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;

@Component
@Path("sn")
public class TurSNSiteAPI {
	
	@Autowired
	TurSNSiteRepository turSNSiteRepository;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurSNSite> list() throws JSONException {
		 return this.turSNSiteRepository.findAll();
	}

	@GET
	@Path("{snSiteId}")
	@Produces(MediaType.APPLICATION_JSON)
	public TurSNSite dataGroup(@PathParam("snSiteId") int id) throws JSONException {
		 return this.turSNSiteRepository.findById(id);
	}
	
	@Path("/{snSiteId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurSNSite update(@PathParam("snSiteId") int id, TurSNSite turSNSite) throws Exception {
		TurSNSite turSNSiteEdit = this.turSNSiteRepository.findById(id);
		turSNSiteEdit.setName(turSNSite.getName());
		turSNSiteEdit.setDescription(turSNSite.getDescription());
		turSNSiteEdit.setLanguage(turSNSite.getLanguage());
		turSNSiteEdit.setTurSEInstance(turSNSite.getTurSEInstance());
		turSNSiteEdit.setTurNLPInstance(turSNSite.getTurNLPInstance());
		turSNSiteEdit.setCore(turSNSite.getCore());
		//UI
		turSNSiteEdit.setFacet(turSNSite.getFacet());
		turSNSiteEdit.setHl(turSNSite.getHl());
		turSNSiteEdit.setHlPost(turSNSite.getHlPost());
		turSNSiteEdit.setHlPre(turSNSite.getHlPre());
		turSNSiteEdit.setItemsPerFacet(turSNSite.getItemsPerFacet());
		turSNSiteEdit.setMlt(turSNSite.getMlt());
		turSNSiteEdit.setRowsPerPage(turSNSite.getRowsPerPage());
		this.turSNSiteRepository.save(turSNSiteEdit);
		return turSNSiteEdit;
	}

	@Path("/{snSiteId}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("snSiteId") int id) throws Exception {
		this.turSNSiteRepository.delete(id);
		return true;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurSNSite add(TurSNSite turSNSite) throws Exception {
		this.turSNSiteRepository.save(turSNSite);
		return turSNSite;

	}

}