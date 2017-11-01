package com.viglet.turing.api.sn;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.solr.TurSolr;

@Component
public class TurSNProcessQueue {
	@Autowired
	TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	TurSEInstanceRepository turSEInstanceRepository;
	@Autowired
	TurConfigVarRepository turConfigVarRepository;
	@Autowired
	TurSolr turSolr;

	@JmsListener(destination = "sample.queue")
	public void receiveQueue(String json) {
		JSONArray jsonRows = new JSONArray(json);
		try {
			for (int i = 0; i < jsonRows.length(); i++) {
				JSONObject jsonRow = jsonRows.getJSONObject(i);
				turSolr.init(Integer.parseInt(this.turConfigVarRepository.findById("DEFAULT_NLP").getValue()),
						Integer.parseInt(this.turConfigVarRepository.findById("DEFAULT_SE").getValue()), jsonRow);
				turSolr.indexing();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Received message");
	}

}
