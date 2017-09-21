package com.viglet.turing.listener.onstartup.nlp;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class TurOpenNLPModelsOnStartup {
	private static final String LOCALE_EN = "en";
	private static final String LOCALE_PT = "pt";
	private static int numberOfDonwloads = 0;

	public static void downloadModels() {

		downloadModel(LOCALE_EN, "http://opennlp.sourceforge.net/models-1.5/en-ner-date.bin", "en-ner-date.bin");
		downloadModel(LOCALE_EN, "http://opennlp.sourceforge.net/models-1.5/en-ner-person.bin", "en-ner-person.bin");
		downloadModel(LOCALE_EN, "http://opennlp.sourceforge.net/models-1.5/en-ner-location.bin",
				"en-ner-location.bin");
		downloadModel(LOCALE_EN, "http://opennlp.sourceforge.net/models-1.5/en-ner-money.bin", "en-ner-money.bin");
		downloadModel(LOCALE_EN, "http://opennlp.sourceforge.net/models-1.5/en-ner-organization.bin",
				"en-ner-organization.bin");
		downloadModel(LOCALE_EN, "http://opennlp.sourceforge.net/models-1.5/en-ner-percentage.bin",
				"en-ner-percentage.bin");
		downloadModel(LOCALE_EN, "http://opennlp.sourceforge.net/models-1.5/en-ner-time.bin", "en-ner-time.bin");
		downloadModel(LOCALE_EN, "http://opennlp.sourceforge.net/models-1.5/en-parser-chunking.bin",
				"en-parser-chunking.bin");
		downloadModel(LOCALE_EN, "http://opennlp.sourceforge.net/models-1.5/en-pos-maxent.bin", "en-pos-maxent.bin");
		downloadModel(LOCALE_EN, "http://opennlp.sourceforge.net/models-1.5/en-pos-perceptron.bin",
				"en-pos-perceptron.bin");
		downloadModel(LOCALE_EN, "http://opennlp.sourceforge.net/models-1.5/en-sent.bin", "en-sent.bin");
		downloadModel(LOCALE_EN, "http://opennlp.sourceforge.net/models-1.5/en-token.bin", "en-token.bin");

		downloadModel(LOCALE_PT, "http://opennlp.sourceforge.net/models-1.5/pt-pos-maxent.bin", "pt-pos-maxent.bin");
		downloadModel(LOCALE_PT, "http://opennlp.sourceforge.net/models-1.5/pt-pos-perceptron.bin",
				"pt-pos-perceptron.bin");
		downloadModel(LOCALE_PT, "http://opennlp.sourceforge.net/models-1.5/pt-sent.bin", "pt-sent.bin");
		downloadModel(LOCALE_PT, "http://opennlp.sourceforge.net/models-1.5/pt-token.bin", "pt-token.bin");
		if (numberOfDonwloads >= 1) {
			System.out.println("OpenNLP Models downloaded...");
		}
	}

	public static void downloadModel(String locale, String fromFile, String toFile) {
		String toFileFullPath = "src/main/resources/models/opennlp/" + locale + "/" + toFile;
		File file = new File(toFileFullPath);
		if (!file.exists()) {
			numberOfDonwloads++;
			if (numberOfDonwloads == 1) {
				System.out.println("Downloading OpenNLP Models...");
			}
			
			System.out.print("Downloading " + toFile + " Model...");
			try {
				FileUtils.copyURLToFile(new URL(fromFile), new File(toFileFullPath), 100000, 100000);

			} catch (IOException e) {
				e.printStackTrace();
			}

			System.out.print("[ OK ]");
			System.out.println("");
			
		}	
	}
}
