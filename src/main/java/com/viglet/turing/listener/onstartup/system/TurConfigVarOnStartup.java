package com.viglet.turing.listener.onstartup.system;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

@Component
@Transactional
public class TurConfigVarOnStartup {

	@Autowired
	private TurConfigVarRepository turConfigVarRepository;
	public void createDefaultRows() {

		final String FIRST_TIME = "FIRST_TIME";
		
		TurConfigVar turConfigVar = new TurConfigVar();

		if (turConfigVarRepository.getOne(FIRST_TIME) == null) {
			
			turConfigVar.setId(FIRST_TIME);
			turConfigVar.setPath("/system");
			turConfigVar.setValue("true");
			turConfigVarRepository.save(turConfigVar);
		}
	}

}