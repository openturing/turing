package com.viglet.turing.listener.onstartup.nlp;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.service.nlp.TurNLPEntityService;

public class TurNLPEntityOnStartup {
	public static void createDefaultRows() {
		
		TurNLPEntityService turNLPEntityService = new TurNLPEntityService();
		if (turNLPEntityService.listAll().isEmpty()) {
			TurNLPEntity turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("People");
			turNLPEntity.setInternalName("PN");
			turNLPEntity.setDescription("Entidade de Pessoas");
			turNLPEntity.setLocal(1);
			turNLPEntity.setCollectionName("persons");
			turNLPEntityService.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Places");
			turNLPEntity.setInternalName("GL");
			turNLPEntity.setDescription("Entidade de Lugares");
			turNLPEntity.setLocal(1);
			turNLPEntity.setCollectionName("locations");
			turNLPEntityService.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Fraud");
			turNLPEntity.setInternalName("FR");
			turNLPEntity.setDescription("Entidade de Fraude");
			turNLPEntity.setLocal(1);
			turNLPEntity.setCollectionName("frauds");
			turNLPEntityService.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Organization");
			turNLPEntity.setInternalName("ON");
			turNLPEntity.setDescription("Entidades de Organizações");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("organizations");
			turNLPEntityService.save(turNLPEntity);
						
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Duration");
			turNLPEntity.setInternalName("DURATION");
			turNLPEntity.setDescription("Entidade de Durações");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("durations");
			turNLPEntityService.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Ordinal");
			turNLPEntity.setInternalName("ORDINAL");
			turNLPEntity.setDescription("Entidade Ordinal");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("ordinals");
			turNLPEntityService.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Misc");
			turNLPEntity.setInternalName("MISC");
			turNLPEntity.setDescription("Entidade Misc");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("miscs");
			turNLPEntityService.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Date");
			turNLPEntity.setInternalName("DATE");
			turNLPEntity.setDescription("Entidade Date");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("dates");
			turNLPEntityService.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Time");
			turNLPEntity.setInternalName("TIME");
			turNLPEntity.setDescription("Entidade Time");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("times");
			turNLPEntityService.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Money");
			turNLPEntity.setInternalName("MONEY");
			turNLPEntity.setDescription("Entidade Money");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("moneys");
			turNLPEntityService.save(turNLPEntity);
			
			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Percentage");
			turNLPEntity.setInternalName("PERCENTAGE");
			turNLPEntity.setDescription("Entidade de Porcentagem");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("percentages");
			turNLPEntityService.save(turNLPEntity);
		}
	}
}
