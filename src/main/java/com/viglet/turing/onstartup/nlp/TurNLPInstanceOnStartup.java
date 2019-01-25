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
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

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

			TurNLPVendor turNLPVendorOpenNLP = turNLPVendorRepository.findById("OPENNLP").get();
			
			
			if (turNLPVendorOpenNLP != null) {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("OpenNLP English");
				turNLPInstance.setDescription("OpenNLP Production - English");
				turNLPInstance.setTurNLPVendor(turNLPVendorOpenNLP);
				turNLPInstance.setHost("");
				turNLPInstance.setPort(0);
				turNLPInstance.setLanguage(TurLocaleRepository.EN_US);
				turNLPInstance.setEnabled(1);
				turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);

				turConfigVar.setId("DEFAULT_NLP");
				turConfigVar.setPath("/nlp");
				turConfigVar.setValue(Integer.toString(turNLPInstance.getId()));
				turConfigVarRepository.save(turConfigVar);
				
				turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("OpenNLP Portuguese");
				turNLPInstance.setDescription("OpenNLP Production - Portuguese");
				turNLPInstance.setTurNLPVendor(turNLPVendorOpenNLP);
				turNLPInstance.setHost("");
				turNLPInstance.setPort(0);
				turNLPInstance.setLanguage(TurLocaleRepository.PT_BR);
				turNLPInstance.setEnabled(1);
				turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);

			}

			TurNLPVendor turNLPVendorCoreNLP = turNLPVendorRepository.findById("CORENLP").get();
			if (turNLPVendorCoreNLP != null) {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("CoreNLP");
				turNLPInstance.setDescription("CoreNLP Production - English");
				turNLPInstance.setTurNLPVendor(turNLPVendorCoreNLP);
				turNLPInstance.setHost("localhost");
				turNLPInstance.setPort(9001);
				turNLPInstance.setLanguage(TurLocaleRepository.EN_US);
				turNLPInstance.setEnabled(1);
				turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);

			}
			
			TurNLPVendor turNLPVendorSpaCy = turNLPVendorRepository.findById("SPACY").get();
			if (turNLPVendorCoreNLP != null) {
				TurNLPInstance turNLPInstance = new TurNLPInstance();
				turNLPInstance.setTitle("SpaCy");
				turNLPInstance.setDescription("SpaCy Production - English");
				turNLPInstance.setTurNLPVendor(turNLPVendorSpaCy);
				turNLPInstance.setHost("localhost");
				turNLPInstance.setPort(8080);
				turNLPInstance.setLanguage(TurLocaleRepository.EN_US);
				turNLPInstance.setEnabled(1);
				turNLPInstanceRepository.saveAndAssocEntity(turNLPInstance);

			}

		}

	}
}
