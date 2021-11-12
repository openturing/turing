/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.onstartup.nlp;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class TurOpenNLPModelsOnStartup {
	public TurOpenNLPModelsOnStartup() {
		super();
	}

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

		File userDir = new File(System.getProperty("user.dir"));
		if (userDir.exists() && userDir.isDirectory()) {
			File modelDir = new File(userDir.getAbsolutePath().concat(File.separator).concat("models")
					.concat(File.separator).concat("opennlp").concat(File.separator).concat(locale));
			if (!modelDir.exists()) {
				modelDir.mkdirs();
			}

			String toFileFullPath = modelDir.getAbsolutePath().concat(File.separator).concat(toFile);
			System.out.println("Path: " + toFileFullPath);
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
}
