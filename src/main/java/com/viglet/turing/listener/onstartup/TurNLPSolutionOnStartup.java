package com.viglet.turing.listener.onstartup;

import com.viglet.turing.persistence.model.TurNLPSolution;
import com.viglet.turing.persistence.service.TurNLPSolutionService;

public class TurNLPSolutionOnStartup {

	public static void createDefaultRows() {


		TurNLPSolutionService turNLPSolutionService = new TurNLPSolutionService();
		if (turNLPSolutionService.listAll().isEmpty()) {
			
			TurNLPSolution turNLPSolution = new TurNLPSolution();
			turNLPSolution.setDescription("Stanford CoreNLP");
			turNLPSolution.setPlugin("com.viglet.turing.plugins.corenlp.CoreNLPConnector");
			turNLPSolution.setTitle("Stanford CoreNLP");
			turNLPSolution.setWebsite("http://stanfordnlp.github.io/CoreNLP");
			turNLPSolutionService.save(turNLPSolution);

			turNLPSolution = new TurNLPSolution();
			turNLPSolution.setDescription("OpenText OTCA");
			turNLPSolution.setPlugin("com.viglet.turing.plugins.otca.TmeConnector");
			turNLPSolution.setTitle("OpenText OTCA");
			turNLPSolution.setWebsite("http://opentext.com/what-we-do/products/discovery");
			turNLPSolutionService.save(turNLPSolution);

			turNLPSolution = new TurNLPSolution();
			turNLPSolution.setDescription("Apache OpenNLP");
			turNLPSolution.setPlugin("com.viglet.turing.plugins.opennlp.OpenNLPConnector");
			turNLPSolution.setTitle("Apache OpenNLP");
			turNLPSolution.setWebsite("https://opennlp.apache.org");
			turNLPSolutionService.save(turNLPSolution);
			
			turNLPSolution = new TurNLPSolution();
			turNLPSolution.setTitle("SpaCy");
			turNLPSolution.setPlugin(null);
			turNLPSolution.setDescription("SpaCy");
			turNLPSolution.setWebsite("https://spacy.io");
			turNLPSolutionService.save(turNLPSolution);
			
			turNLPSolution = new TurNLPSolution();
			turNLPSolution.setTitle("NTLK");
			turNLPSolution.setPlugin(null);
			turNLPSolution.setDescription("NTLK");
			turNLPSolution.setWebsite("http://www.nltk.org");
			turNLPSolutionService.save(turNLPSolution);
			
			turNLPSolution = new TurNLPSolution();
			turNLPSolution.setTitle("Google SyntaxNet");
			turNLPSolution.setPlugin(null);
			turNLPSolution.setDescription("Google SyntaxNet");
			turNLPSolution.setWebsite("https://www.tensorflow.org/versions/master/tutorials/syntaxnet/index.html");
			turNLPSolutionService.save(turNLPSolution);
			
			turNLPSolution = new TurNLPSolution();
			turNLPSolution.setTitle("MALLET");
			turNLPSolution.setPlugin(null);
			turNLPSolution.setDescription("MALLET");
			turNLPSolution.setWebsite("http://mallet.cs.umass.edu");
			turNLPSolutionService.save(turNLPSolution);
			
			turNLPSolution = new TurNLPSolution();
			turNLPSolution.setTitle("ClearNLP");
			turNLPSolution.setPlugin(null);
			turNLPSolution.setDescription("ClearNLP");
			turNLPSolution.setWebsite("http://www.clearnlp.com");
			turNLPSolutionService.save(turNLPSolution);
			
			turNLPSolution = new TurNLPSolution();
			turNLPSolution.setTitle("VigletNLP");
			turNLPSolution.setPlugin(null);
			turNLPSolution.setDescription("VigletNLP");
			turNLPSolution.setWebsite("http://www.viglet.ai");
			turNLPSolutionService.save(turNLPSolution);
			
		}
	}

}
