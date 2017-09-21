package com.viglet.turing.onstartup.nlp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.nlp.TurNLPInstanceRepository;
import com.viglet.turing.persistence.repository.nlp.TurNLPVendorRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

@Component
@Transactional
public class TurNLPInstanceOnStartup {

	@Autowired
	private TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	private TurNLPVendorRepository turNLPVendorRepository;
	@Autowired
	private TurConfigVarRepository turConfigVarRepository;

	public void createDefaultRows() {

		TurConfigVar turConfigVar = new TurConfigVar();

		if (turNLPInstanceRepository.findAll().isEmpty()) {

			TurNLPVendor turNLPVendorOpenNLP = turNLPVendorRepository.findOne("OPENNLP");
			if (turNLPVendorOpenNLP != null) {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("OpenNLP");
				turNLPInstance.setDescription("OpenNLP Production");
				turNLPInstance.setTurNLPVendor(turNLPVendorOpenNLP);
				turNLPInstance.setHost("");
				turNLPInstance.setPort(0);
				turNLPInstance.setLanguage("pt-br");
				turNLPInstance.setEnabled(1);
				turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);

				turConfigVar.setId("DEFAULT_NLP");
				turConfigVar.setPath("/nlp");
				turConfigVar.setValue(Integer.toString(turNLPInstance.getId()));
				turConfigVarRepository.save(turConfigVar);

			}

			TurNLPVendor turNLPVendorCoreNLP = turNLPVendorRepository.findOne("CORENLP");
			if (turNLPVendorCoreNLP != null) {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("CoreNLP");
				turNLPInstance.setDescription("CoreNLP Production");
				turNLPInstance.setTurNLPVendor(turNLPVendorCoreNLP);
				turNLPInstance.setHost("localhost");
				turNLPInstance.setPort(9001);
				turNLPInstance.setLanguage("en");
				turNLPInstance.setEnabled(1);
				turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);

			}

		}

	}
}
