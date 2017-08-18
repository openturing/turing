package com.viglet.turing.listener.onstartup.system;

import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.service.system.TurConfigVarService;

public class TurConfigVarOnStartup {

	public static void createDefaultRows() {

		final String FIRST_TIME = "FIRST_TIME";
		TurConfigVarService turConfigVarService = new TurConfigVarService();
		TurConfigVar turConfigVar = new TurConfigVar();

		if (turConfigVarService.get(FIRST_TIME) == null) {
			
			turConfigVar.setId(FIRST_TIME);
			turConfigVar.setPath("/system");
			turConfigVar.setValue("true");
			turConfigVarService.save(turConfigVar);
		}
	}

}