package com.viglet.turing.listener.onstartup;

import com.viglet.turing.persistence.model.TurEntity;
import com.viglet.turing.persistence.service.TurEntityService;

public class TurEntityOnStartup {
	public static void createDefaultRows() {
		
		TurEntityService turEntityService = new TurEntityService();
		if (turEntityService.listAll().isEmpty()) {
			TurEntity turEntity = new TurEntity();
			turEntity.setName("People");
			turEntity.setInternalName("PN");
			turEntity.setDescription("Entidade de Pessoas");
			turEntity.setLocal(1);
			turEntity.setCollectionName("persons");
			turEntityService.save(turEntity);
			
			turEntity = new TurEntity();
			turEntity.setName("Places");
			turEntity.setInternalName("GL");
			turEntity.setDescription("Entidade de Lugares");
			turEntity.setLocal(1);
			turEntity.setCollectionName("locations");
			turEntityService.save(turEntity);

			turEntity = new TurEntity();
			turEntity.setName("Fraud");
			turEntity.setInternalName("FR");
			turEntity.setDescription("Entidade de Fraude");
			turEntity.setLocal(1);
			turEntity.setCollectionName("frauds");
			turEntityService.save(turEntity);
			
			turEntity = new TurEntity();
			turEntity.setName("Organization");
			turEntity.setInternalName("ON");
			turEntity.setDescription("Entidades de Organizações");
			turEntity.setLocal(0);
			turEntity.setCollectionName("organizations");
			turEntityService.save(turEntity);
						
			turEntity = new TurEntity();
			turEntity.setName("Duration");
			turEntity.setInternalName("DURATION");
			turEntity.setDescription("Entidade de Durações");
			turEntity.setLocal(0);
			turEntity.setCollectionName("durations");
			turEntityService.save(turEntity);
			
			turEntity = new TurEntity();
			turEntity.setName("Ordinal");
			turEntity.setInternalName("ORDINAL");
			turEntity.setDescription("Entidade Ordinal");
			turEntity.setLocal(0);
			turEntity.setCollectionName("ordinals");
			turEntityService.save(turEntity);
			
			turEntity = new TurEntity();
			turEntity.setName("Misc");
			turEntity.setInternalName("MISC");
			turEntity.setDescription("Entidade Misc");
			turEntity.setLocal(0);
			turEntity.setCollectionName("miscs");
			turEntityService.save(turEntity);
			
			turEntity = new TurEntity();
			turEntity.setName("Date");
			turEntity.setInternalName("DATE");
			turEntity.setDescription("Entidade Date");
			turEntity.setLocal(0);
			turEntity.setCollectionName("dates");
			turEntityService.save(turEntity);
			
			turEntity = new TurEntity();
			turEntity.setName("Time");
			turEntity.setInternalName("TIME");
			turEntity.setDescription("Entidade Time");
			turEntity.setLocal(0);
			turEntity.setCollectionName("times");
			turEntityService.save(turEntity);
			
			turEntity = new TurEntity();
			turEntity.setName("Money");
			turEntity.setInternalName("MONEY");
			turEntity.setDescription("Entidade Money");
			turEntity.setLocal(0);
			turEntity.setCollectionName("moneys");
			turEntityService.save(turEntity);
			
			turEntity = new TurEntity();
			turEntity.setName("Percentage");
			turEntity.setInternalName("PERCENTAGE");
			turEntity.setDescription("Entidade de Porcentagem");
			turEntity.setLocal(0);
			turEntity.setCollectionName("percentages");
			turEntityService.save(turEntity);
		}
	}
}
