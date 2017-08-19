package com.viglet.turing.api.ml.data;

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

import com.viglet.turing.persistence.model.system.TurLocale;
import com.viglet.turing.persistence.service.system.TurLocaleService;

@Path("/locale")
public class TurLocaleAPI {
	TurLocaleService turLocaleService = new TurLocaleService();

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurLocale> list() throws JSONException {
		return turLocaleService.listAll();
	}

	@Path("{initials}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurLocale detail(@PathParam("initials") String id) throws JSONException {
		return turLocaleService.get(id);
	}

	@Path("/{initials}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurLocale update(@PathParam("initials") String initials, TurLocale turLocale) throws Exception {
		TurLocale turLocaleEdit = turLocaleService.get(initials);
		turLocaleEdit.setEn(turLocale.getEn());
		turLocaleEdit.setPt(turLocale.getPt());
		turLocaleService.save(turLocaleEdit);
		return turLocaleEdit;
	}

	@Path("/{initials}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("initials") String id) throws Exception {
		return turLocaleService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurLocale add(TurLocale turLocale) throws Exception {
		turLocaleService.save(turLocale);
		return turLocale;

	}
}
