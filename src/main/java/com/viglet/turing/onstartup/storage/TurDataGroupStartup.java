package com.viglet.turing.onstartup.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.repository.storage.TurDataGroupRepository;

@Component
@Transactional
public class TurDataGroupStartup {

	@Autowired
	private  TurDataGroupRepository turDataGroupRepository;
	
	public void createDefaultRows() {

		if (turDataGroupRepository.findAll().isEmpty()) {
			
			TurDataGroup turDataGroup = new TurDataGroup();
			turDataGroup.setName("Sample");
			turDataGroup.setDescription("A Sample Data Group");
			turDataGroupRepository.save(turDataGroup);
			
		}
	}
}
