package com.viglet.turing.listener.onstartup.ml;

import com.viglet.turing.persistence.model.ml.TurMLVendor;
import com.viglet.turing.persistence.service.ml.TurMLVendorService;

public class TurMLVendorOnStartup {

	public static void createDefaultRows() {


		TurMLVendorService turMLVendorService = new TurMLVendorService();
		if (turMLVendorService.listAll().isEmpty()) {
			
			TurMLVendor turMLVendor = new TurMLVendor();
			turMLVendor.setId("OPENNLP");
			turMLVendor.setDescription("Apache OpenNLP");
			turMLVendor.setPlugin("");
			turMLVendor.setTitle("Apache OpenNLP");
			turMLVendor.setWebsite("https://opennlp.apache.org");
			turMLVendorService.save(turMLVendor);
			
			turMLVendor = new TurMLVendor();
			turMLVendor.setId("MAHOUT");
			turMLVendor.setDescription("Apache Mahout");
			turMLVendor.setPlugin("");
			turMLVendor.setTitle("Apache Mahout");
			turMLVendor.setWebsite("https://mahout.apache.org");
			turMLVendorService.save(turMLVendor);
			
			turMLVendor = new TurMLVendor();
			turMLVendor.setId("WATSON");
			turMLVendor.setDescription("IBM Watson");
			turMLVendor.setPlugin("");
			turMLVendor.setTitle("IBM Watson");
			turMLVendor.setWebsite("http://www.ibm.com/watson");
			turMLVendorService.save(turMLVendor);
			
			turMLVendor = new TurMLVendor();
			turMLVendor.setId("TENSORFLOW");
			turMLVendor.setDescription("Google TensorFlow");
			turMLVendor.setPlugin("");
			turMLVendor.setTitle("Google TensorFlow");
			turMLVendor.setWebsite("https://www.tensorflow.org");
			turMLVendorService.save(turMLVendor);
			
			turMLVendor = new TurMLVendor();
			turMLVendor.setId("CNTK");
			turMLVendor.setDescription("Microsoft CNTK");
			turMLVendor.setPlugin("");
			turMLVendor.setTitle("Microsoft CNTK");
			turMLVendor.setWebsite("https://github.com/Microsoft/CNTK");
			turMLVendorService.save(turMLVendor);
			
			turMLVendor = new TurMLVendor();
			turMLVendor.setId("PREDICTIONIO");
			turMLVendor.setDescription("SalesForce PredictionIO");
			turMLVendor.setPlugin("");
			turMLVendor.setTitle("SalesForce PredictionIO");
			turMLVendor.setWebsite("https://prediction.io");
			turMLVendorService.save(turMLVendor);
			
			turMLVendor = new TurMLVendor();
			turMLVendor.setId("SCIKIT");
			turMLVendor.setDescription("scikit-learn");
			turMLVendor.setPlugin("");
			turMLVendor.setTitle("scikit-learn");
			turMLVendor.setWebsite("http://scikit-learn.org");
			turMLVendorService.save(turMLVendor);
			
			turMLVendor = new TurMLVendor();
			turMLVendor.setId("SHOGUN");
			turMLVendor.setDescription("Shogun");
			turMLVendor.setPlugin("");
			turMLVendor.setTitle("Shogun");
			turMLVendor.setWebsite("http://www.shogun-toolbox.org");
			turMLVendorService.save(turMLVendor);
			
			turMLVendor = new TurMLVendor();
			turMLVendor.setId("WEKA");
			turMLVendor.setDescription("Weka");
			turMLVendor.setPlugin("");
			turMLVendor.setTitle("Weka");
			turMLVendor.setWebsite("http://www.cs.waikato.ac.nz/ml/weka");
			turMLVendorService.save(turMLVendor);
		}
	}

}