package com.viglet.turing.api.ml.data.group;

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

import com.viglet.turing.bean.ml.sentence.TurMLSentenceBean;
import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.service.storage.TurDataGroupSentenceService;
import com.viglet.turing.persistence.service.storage.TurDataGroupService;

@Path("/ml/data/group/{dataGroupId}/sentence")
public class TurMLDataGroupSentenceAPI {
	TurDataGroupService turDataGroupService = new TurDataGroupService();
	TurDataGroupSentenceService turDataGroupSentenceService = new TurDataGroupSentenceService();

	@GET
	@Produces("application/json")
	public List<TurDataGroupSentence> list(@PathParam("dataGroupId") int dataGroupId) throws JSONException {
		TurDataGroup turDataGroup = turDataGroupService.get(dataGroupId);
		return turDataGroupSentenceService.findByDataGroup(turDataGroup);
	}

	@Path("{dataGroupSentenceId}")
	@GET
	@Produces("application/json")
	public TurDataGroupSentence mlSolution(@PathParam("dataGroupId") int dataGroupId,
			@PathParam("dataGroupSentenceId") int id) throws JSONException {
		return turDataGroupSentenceService.get(id);
	}

	@Path("/{dataGroupSentenceId}")
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	public TurDataGroupSentence update(@PathParam("dataGroupId") int dataGroupId,
			@PathParam("dataGroupSentenceId") int id, TurDataGroupSentence turDataGroupSentence) throws Exception {
		TurDataGroupSentence turDataGroupSentenceEdit = turDataGroupSentenceService.get(id);
		turDataGroupSentenceEdit.setSentence(turDataGroupSentence.getSentence());
		turDataGroupSentenceEdit.setTurMLCategory(turDataGroupSentence.getTurMLCategory());
		turDataGroupSentenceService.save(turDataGroupSentenceEdit);
		return turDataGroupSentenceEdit;
	}

	@Path("{dataGroupSentenceId}")
	@DELETE
	@Produces("application/json")
	public boolean deleteEntity(@PathParam("dataGroupId") int dataGroupId, @PathParam("dataGroupSentenceId") int id) {
		return turDataGroupSentenceService.delete(id);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public TurDataGroupSentence add(@PathParam("dataGroupId") int dataGroupId, TurMLSentenceBean turMLSentenceBean)
			throws Exception {
		TurDataGroupSentence turDataGroupSentence = new TurDataGroupSentence();
		TurDataGroup turDataGroup = turDataGroupService.get(dataGroupId);
		turDataGroupSentence.setSentence(turMLSentenceBean.getSentence());
		turDataGroupSentence.setTurDataGroup(turDataGroup);
		turDataGroupSentenceService.save(turDataGroupSentence);
		return turDataGroupSentence;

	}
}
