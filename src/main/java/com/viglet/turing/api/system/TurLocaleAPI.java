package com.viglet.turing.api.system;

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

import com.viglet.turing.persistence.model.system.TurLocale;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

@Component
@Path("locale")
public class TurLocaleAPI {

	@Autowired
	TurLocaleRepository turLocaleRepository;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<TurLocale> list() throws JSONException {
		return this.turLocaleRepository.findAll();
	}

	@Path("{initials}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public TurLocale detail(@PathParam("initials") String id) throws JSONException {
		return this.turLocaleRepository.findOne(id);
	}

	@Path("/{initials}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurLocale update(@PathParam("initials") String id, TurLocale turLocale) throws Exception {
		TurLocale turLocaleEdit = this.turLocaleRepository.findOne(id);
		turLocaleEdit.setEn(turLocale.getEn());
		turLocaleEdit.setPt(turLocale.getPt());
		this.turLocaleRepository.save(turLocaleEdit);
		return turLocaleEdit;
	}

	@Path("/{initials}")
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	public boolean delete(@PathParam("initials") String id) throws Exception {
		this.turLocaleRepository.delete(id);
		return true;
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurLocale add(TurLocale turLocale) throws Exception {
		this.turLocaleRepository.save(turLocale);
		return turLocale;

	}
}
