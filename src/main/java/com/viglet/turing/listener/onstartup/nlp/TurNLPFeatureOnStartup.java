package com.viglet.turing.listener.onstartup.nlp;

import com.viglet.turing.persistence.model.nlp.TurNLPFeature;
import com.viglet.turing.persistence.service.nlp.TurNLPFeatureService;

public class TurNLPFeatureOnStartup {

	public static void createDefaultRows() {


		TurNLPFeatureService turNLPFeatureService = new TurNLPFeatureService();
		if (turNLPFeatureService.listAll().isEmpty()) {
			
			TurNLPFeature turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Tokenize / Segment");
			turNLPFeature.setDescription("Tokenize / Segment");
			turNLPFeatureService.save(turNLPFeature);

			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Sentence Split");
			turNLPFeature.setDescription("Sentence Split");
			turNLPFeatureService.save(turNLPFeature);

			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Part of Speech");
			turNLPFeature.setDescription("Part of Speech");
			turNLPFeatureService.save(turNLPFeature);
			
			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Lemma");
			turNLPFeature.setDescription("Lemma");
			turNLPFeatureService.save(turNLPFeature);
			
			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Named Entities");
			turNLPFeature.setDescription("Named Entities");
			turNLPFeatureService.save(turNLPFeature);
			
			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Constituency Parsing");
			turNLPFeature.setDescription("Constituency Parsing");
			turNLPFeatureService.save(turNLPFeature);
			
			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Dependency Parsing");
			turNLPFeature.setDescription("Dependency Parsing");
			turNLPFeatureService.save(turNLPFeature);
			
			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Sentiment Analysis");
			turNLPFeature.setDescription("Sentiment Analysis");
			turNLPFeatureService.save(turNLPFeature);
			
			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Mention Detection");
			turNLPFeature.setDescription("Mention Detection");
			turNLPFeatureService.save(turNLPFeature);
			
			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Coreference");
			turNLPFeature.setDescription("Coreference");
			turNLPFeatureService.save(turNLPFeature);
			
			turNLPFeature = new TurNLPFeature();
			turNLPFeature.setTitle("Open IE");
			turNLPFeature.setDescription("Open IE");
			turNLPFeatureService.save(turNLPFeature);
						
		}
	}

}
