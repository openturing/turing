/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.onstartup.nlp;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TurOpenNLPModelsOnStartup {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());
	// Reset
	public static final String RESET = "\033[0m"; // Text Reset

	// Regular Colors
	public static final String BLACK = "\033[0;30m"; // BLACK
	public static final String RED = "\033[0;31m"; // RED
	public static final String GREEN = "\033[0;32m"; // GREEN
	public static final String YELLOW = "\033[0;33m"; // YELLOW
	public static final String BLUE = "\033[0;34m"; // BLUE
	public static final String PURPLE = "\033[0;35m"; // PURPLE
	public static final String CYAN = "\033[0;36m"; // CYAN
	public static final String WHITE = "\033[0;37m"; // WHITE

	// Bold
	public static final String BLACK_BOLD = "\033[1;30m"; // BLACK
	public static final String RED_BOLD = "\033[1;31m"; // RED
	public static final String GREEN_BOLD = "\033[1;32m"; // GREEN
	public static final String YELLOW_BOLD = "\033[1;33m"; // YELLOW
	public static final String BLUE_BOLD = "\033[1;34m"; // BLUE
	public static final String PURPLE_BOLD = "\033[1;35m"; // PURPLE
	public static final String CYAN_BOLD = "\033[1;36m"; // CYAN
	public static final String WHITE_BOLD = "\033[1;37m"; // WHITE

	// Underline
	public static final String BLACK_UNDERLINED = "\033[4;30m"; // BLACK
	public static final String RED_UNDERLINED = "\033[4;31m"; // RED
	public static final String GREEN_UNDERLINED = "\033[4;32m"; // GREEN
	public static final String YELLOW_UNDERLINED = "\033[4;33m"; // YELLOW
	public static final String BLUE_UNDERLINED = "\033[4;34m"; // BLUE
	public static final String PURPLE_UNDERLINED = "\033[4;35m"; // PURPLE
	public static final String CYAN_UNDERLINED = "\033[4;36m"; // CYAN
	public static final String WHITE_UNDERLINED = "\033[4;37m"; // WHITE

	// Background
	public static final String BLACK_BACKGROUND = "\033[40m"; // BLACK
	public static final String RED_BACKGROUND = "\033[41m"; // RED
	public static final String GREEN_BACKGROUND = "\033[42m"; // GREEN
	public static final String YELLOW_BACKGROUND = "\033[43m"; // YELLOW
	public static final String BLUE_BACKGROUND = "\033[44m"; // BLUE
	public static final String PURPLE_BACKGROUND = "\033[45m"; // PURPLE
	public static final String CYAN_BACKGROUND = "\033[46m"; // CYAN
	public static final String WHITE_BACKGROUND = "\033[47m"; // WHITE

	// High Intensity
	public static final String BLACK_BRIGHT = "\033[0;90m"; // BLACK
	public static final String RED_BRIGHT = "\033[0;91m"; // RED
	public static final String GREEN_BRIGHT = "\033[0;92m"; // GREEN
	public static final String YELLOW_BRIGHT = "\033[0;93m"; // YELLOW
	public static final String BLUE_BRIGHT = "\033[0;94m"; // BLUE
	public static final String PURPLE_BRIGHT = "\033[0;95m"; // PURPLE
	public static final String CYAN_BRIGHT = "\033[0;96m"; // CYAN
	public static final String WHITE_BRIGHT = "\033[0;97m"; // WHITE

	// Bold High Intensity
	public static final String BLACK_BOLD_BRIGHT = "\033[1;90m"; // BLACK
	public static final String RED_BOLD_BRIGHT = "\033[1;91m"; // RED
	public static final String GREEN_BOLD_BRIGHT = "\033[1;92m"; // GREEN
	public static final String YELLOW_BOLD_BRIGHT = "\033[1;93m";// YELLOW
	public static final String BLUE_BOLD_BRIGHT = "\033[1;94m"; // BLUE
	public static final String PURPLE_BOLD_BRIGHT = "\033[1;95m";// PURPLE
	public static final String CYAN_BOLD_BRIGHT = "\033[1;96m"; // CYAN
	public static final String WHITE_BOLD_BRIGHT = "\033[1;97m"; // WHITE

	// High Intensity backgrounds
	public static final String BLACK_BACKGROUND_BRIGHT = "\033[0;100m";// BLACK
	public static final String RED_BACKGROUND_BRIGHT = "\033[0;101m";// RED
	public static final String GREEN_BACKGROUND_BRIGHT = "\033[0;102m";// GREEN
	public static final String YELLOW_BACKGROUND_BRIGHT = "\033[0;103m";// YELLOW
	public static final String BLUE_BACKGROUND_BRIGHT = "\033[0;104m";// BLUE
	public static final String PURPLE_BACKGROUND_BRIGHT = "\033[0;105m"; // PURPLE
	public static final String CYAN_BACKGROUND_BRIGHT = "\033[0;106m"; // CYAN
	public static final String WHITE_BACKGROUND_BRIGHT = "\033[0;107m"; // WHITE

	private TurOpenNLPModelsOnStartup() {
		throw new IllegalStateException("OpenNLP Models class");
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
			logger.info("OpenNLP Models downloaded...");
		}
		logger.info(" ");
	}

	public static void downloadModel(String locale, String fromFile, String toFile) {

		File userDir = new File(System.getProperty("user.dir"));
		if (userDir.exists() && userDir.isDirectory()) {
			File modelDir = new File(userDir.getAbsolutePath().concat(File.separator).concat("models")
					.concat(File.separator).concat("opennlp").concat(File.separator).concat(locale));
			try {
				Files.createDirectories(modelDir.toPath());
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}
			String toFileFullPath = modelDir.getAbsolutePath().concat(File.separator).concat(toFile);

			File file = new File(toFileFullPath);
			if (!file.exists()) {
				
				numberOfDonwloads++;
				if (numberOfDonwloads == 1) {
					logger.info("Downloading OpenNLP Models...");
				}
				logger.info("..Downloading {} model...", toFile);
				try {
					FileUtils.copyURLToFile(new URL(fromFile), new File(toFileFullPath), 100000, 100000);

				} catch (IOException e) {
					logger.error(e.getMessage(), e);
				}
				logger.info("....[ {}OK{} ]", GREEN_BOLD_BRIGHT, RESET);
			} else {
				logger.info("[ {}OK{} ] {} model ",  GREEN_BOLD_BRIGHT, RESET, toFile);
			}
		}
	}
}
