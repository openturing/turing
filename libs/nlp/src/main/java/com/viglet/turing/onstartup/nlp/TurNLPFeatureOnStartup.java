package com.viglet.turing.onstartup.nlp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.nlp.TurNLPFeature;
import com.viglet.turing.persistence.repository.nlp.TurNLPFeatureRepository;

@Component
@Transactional
public class TurNLPFeatureOnStartup {

	@Autowired
	private TurNLPFeatureRepository turNLPFeatureRepository;

	public void createDefaultRows() {

		if (turNLPFeatureRepository.findAll().isEmpty()) {

			TurNLPFeature turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Tokenize / Segment");
			turNLPFeature.setDescription("Tokenize / Segment");
			turNLPFeatureRepository.save(turNLPFeature);

			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Sentence Split");
			turNLPFeature.setDescription("Sentence Split");
			turNLPFeatureRepository.save(turNLPFeature);

			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Part of Speech");
			turNLPFeature.setDescription("Part of Speech");
			turNLPFeatureRepository.save(turNLPFeature);

			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Lemma");
			turNLPFeature.setDescription("Lemma");
			turNLPFeatureRepository.save(turNLPFeature);

			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Named Entities");
			turNLPFeature.setDescription("Named Entities");
			turNLPFeatureRepository.save(turNLPFeature);

			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Constituency Parsing");
			turNLPFeature.setDescription("Constituency Parsing");
			turNLPFeatureRepository.save(turNLPFeature);

			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Dependency Parsing");
			turNLPFeature.setDescription("Dependency Parsing");
			turNLPFeatureRepository.save(turNLPFeature);

			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Sentiment Analysis");
			turNLPFeature.setDescription("Sentiment Analysis");
			turNLPFeatureRepository.save(turNLPFeature);

			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Mention Detection");
			turNLPFeature.setDescription("Mention Detection");
			turNLPFeatureRepository.save(turNLPFeature);

			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Coreference");
			turNLPFeature.setDescription("Coreference");
			turNLPFeatureRepository.save(turNLPFeature);

			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Open IE");
			turNLPFeature.setDescription("Open IE");
			turNLPFeatureRepository.save(turNLPFeature);

		}
	}

}
