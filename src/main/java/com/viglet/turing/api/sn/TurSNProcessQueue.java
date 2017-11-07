package com.viglet.turing.api.sn;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
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
	@Autowired
	TurSNSiteRepository turSNSiteRepository;

	@JmsListener(destination = "sample.queue")
	public void receiveQueue(TurSNJob turSNJob) {
		JSONArray jsonRows = new JSONArray(turSNJob.getJson());
		TurSNSite turSNSite = this.turSNSiteRepository.findById(Integer.parseInt(turSNJob.getSiteId()));
		try {
			for (int i = 0; i < jsonRows.length(); i++) {
				JSONObject jsonRow = jsonRows.getJSONObject(i);

				turSolr.init(turSNSite, jsonRow);
				turSolr.indexing();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Received job");
	}

}
