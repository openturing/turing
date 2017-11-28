package com.viglet.turing.onstartup.ml;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.ml.TurMLInstance;
import com.viglet.turing.persistence.model.ml.TurMLVendor;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.ml.TurMLInstanceRepository;
import com.viglet.turing.persistence.repository.ml.TurMLVendorRepository;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

@Component
@Transactional
public class TurMLInstanceOnStartup {
	@Autowired
	private TurMLInstanceRepository turMLInstanceRepository;
	@Autowired
	private TurMLVendorRepository turMLVendorRepository;
	@Autowired
	private TurConfigVarRepository turConfigVarRepository;

	public void createDefaultRows() {

		TurConfigVar turConfigVar = new TurConfigVar();

		if (turMLInstanceRepository.findAll().isEmpty()) {

			TurMLVendor turMLVendor = turMLVendorRepository.getOne("OPENNLP");
			if (turMLVendor != null) {
				TurMLInstance turMLInstance = new TurMLInstance();
				turMLInstance.setTitle("OpenNLP");
				turMLInstance.setDescription("OpenNLP Production");
				turMLInstance.setTurMLVendor(turMLVendor);
				turMLInstance.setHost("");
				turMLInstance.setPort(0);
				turMLInstance.setLanguage(TurLocaleRepository.EN_US);
				turMLInstance.setEnabled(1);
				turMLInstanceRepository.save(turMLInstance);

				turConfigVar.setId("DEFAULT_ML");
				turConfigVar.setPath("/ml");
				turConfigVar.setValue(Integer.toString(turMLInstance.getId()));
				turConfigVarRepository.save(turConfigVar);
			}
		}

	}

}
