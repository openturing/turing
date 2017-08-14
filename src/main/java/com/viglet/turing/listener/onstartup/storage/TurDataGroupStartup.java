package com.viglet.turing.listener.onstartup.storage;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.service.storage.TurDataGroupService;

public class TurDataGroupStartup {

	public static void createDefaultRows() {


		TurDataGroupService turDataGroupService = new TurDataGroupService();
		if (turDataGroupService.listAll().isEmpty()) {
			
			TurDataGroup turDataGroup = new TurDataGroup();
			turDataGroup.setName("Sample");
			turDataGroup.setDescription("A Sample Data Group");
			turDataGroupService.save(turDataGroup);
			
		}
	}
}
