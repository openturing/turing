package com.viglet.turing.api.sn;

import java.util.List;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.solr.TurSolr;

@Component
@Path("sn/import")
public class TurSNImportAPI {

	@Autowired
	TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	TurConfigVarRepository turConfigVarRepository;
	@Autowired
	TurSolr turSolr;

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response broker(String json) throws JSONException {
		JSONArray jsonRows = new JSONArray(json);
		try {
			for (int i = 0; i < jsonRows.length(); i++) {
				JSONObject jsonRow = jsonRows.getJSONObject(i);
				System.out.println("NLP: " + turConfigVarRepository.findById("DEFAULT_NLP").getValue());
				System.out.println("SE: " + turConfigVarRepository.findById("DEFAULT_SE").getValue());
				turSolr.init(Integer.parseInt(this.turConfigVarRepository.findById("DEFAULT_NLP").getValue()),
						Integer.parseInt(this.turConfigVarRepository.findById("DEFAULT_SE").getValue()), jsonRow);
				turSolr.indexing();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return Response.status(200).entity(jsonRows.toString()).build();

	}
}
