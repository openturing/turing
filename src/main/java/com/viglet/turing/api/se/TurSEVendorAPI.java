package com.viglet.turing.api.se;

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

import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.service.se.TurSEVendorService;

@Path("/se/vendor")
public class TurSEVendorAPI {
	TurSEVendorService turSEVendorService = new TurSEVendorService();
	
	@GET
	@Produces("application/json")
	public List<TurSEVendor> list() throws JSONException {
		return turSEVendorService.listAll();
	}

	@Path("{seVendorId}")
	@GET
	@Produces("application/json")
	public TurSEVendor seSolution(@PathParam("seVendorId") String id) throws JSONException {
		return turSEVendorService.get(id);
	}
	

	@Path("/{seVendorId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurSEVendor update(@PathParam("seVendorId") String id, TurSEVendor turSEVendor) throws Exception {
		TurSEVendor turSEVendorEdit = turSEVendorService.get(id);
		turSEVendorEdit.setDescription(turSEVendor.getDescription());
		turSEVendorEdit.setPlugin(turSEVendor.getPlugin());
		turSEVendorEdit.setTitle(turSEVendor.getTitle());
		turSEVendorEdit.setWebsite(turSEVendor.getWebsite());		
		turSEVendorService.save(turSEVendorEdit);
		return turSEVendorEdit;
	}

	@Path("{seVendorId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("seVendorId") String id) {
		return turSEVendorService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurSEVendor add(TurSEVendor turSEVendor) throws Exception {
		turSEVendorService.save(turSEVendor);
		return turSEVendor;

	}
}
