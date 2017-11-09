package com.viglet.turing.onstartup.nlp;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.repository.nlp.TurNLPEntityRepository;

@Component
@Transactional
public class TurNLPEntityOnStartup {
	@Autowired
	DataSource dataSource;

	@Autowired(required = true)
	private TurNLPEntityRepository turNLPEntityRepository;

	public void createDefaultRows() {

		if (turNLPEntityRepository.findAll().isEmpty()) {
			TurNLPEntity turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("People");
			turNLPEntity.setInternalName("PN");
			turNLPEntity.setDescription("Entidade de Pessoas");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("persons");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Places");
			turNLPEntity.setInternalName("GL");
			turNLPEntity.setDescription("Entidade de Lugares");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("locations");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Fraud");
			turNLPEntity.setInternalName("FR");
			turNLPEntity.setDescription("Entidade de Fraude");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("frauds");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Organization");
			turNLPEntity.setInternalName("ON");
			turNLPEntity.setDescription("Entidades de Organizações");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("organizations");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Duration");
			turNLPEntity.setInternalName("DURATION");
			turNLPEntity.setDescription("Entidade de Durações");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("durations");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Ordinal");
			turNLPEntity.setInternalName("ORDINAL");
			turNLPEntity.setDescription("Entidade Ordinal");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("ordinals");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Misc");
			turNLPEntity.setInternalName("MISC");
			turNLPEntity.setDescription("Entidade Misc");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("miscs");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Date");
			turNLPEntity.setInternalName("DATE");
			turNLPEntity.setDescription("Entidade Date");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("dates");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Time");
			turNLPEntity.setInternalName("TIME");
			turNLPEntity.setDescription("Entidade Time");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("times");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Money");
			turNLPEntity.setInternalName("MONEY");
			turNLPEntity.setDescription("Entidade Money");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("moneys");
			turNLPEntityRepository.save(turNLPEntity);

			turNLPEntity = new TurNLPEntity();
			turNLPEntity.setName("Percentage");
			turNLPEntity.setInternalName("PERCENTAGE");
			turNLPEntity.setDescription("Entidade de Porcentagem");
			turNLPEntity.setLocal(0);
			turNLPEntity.setCollectionName("percentages");
			turNLPEntityRepository.save(turNLPEntity);
		}
	}
}
