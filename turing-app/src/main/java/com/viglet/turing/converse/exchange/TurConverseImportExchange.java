/*
 * Copyright (C) 2021 the original author or authors. 
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

package com.viglet.turing.converse.exchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.viglet.turing.converse.exchange.agent.TurConverseAgentExchange;
import com.viglet.turing.converse.exchange.entity.TurConverseEntityEntriesExchange;
import com.viglet.turing.converse.exchange.entity.TurConverseEntityExchange;
import com.viglet.turing.converse.exchange.intent.TurConverseIntentExchange;
import com.viglet.turing.converse.exchange.intent.TurConverseIntentMessageExchange;
import com.viglet.turing.converse.exchange.intent.TurConverseIntentPhraseDataExchange;
import com.viglet.turing.converse.exchange.intent.TurConverseIntentPhraseExchange;
import com.viglet.turing.converse.exchange.intent.TurConverseIntentPhrasesExchange;
import com.viglet.turing.converse.exchange.intent.TurConverseIntentResponseExchange;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.model.converse.intent.TurConversePhrase;
import com.viglet.turing.persistence.model.converse.intent.TurConverseResponse;
import com.viglet.turing.persistence.repository.converse.TurConverseAgentRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseIntentRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePhraseRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseResponseRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.utils.TurUtils;

@Component
public class TurConverseImportExchange {
	static final Logger logger = LogManager.getLogger(TurConverseImportExchange.class.getName());

	@Autowired
	private TurUtils turUtils;
	@Autowired
	private TurConverseAgentRepository turConverseAgentRepository;
	@Autowired
	private TurConverseIntentRepository turConverseIntentRepository;
	@Autowired
	private TurConversePhraseRepository turConversePhraseRepository;
	@Autowired
	private TurConverseResponseRepository turConverseResponseRepository;
	@Autowired
	private TurSEInstanceRepository turSEInstanceRepository;

	private Map<String, Object> shObjects = new HashMap<String, Object>();
	private Map<String, List<String>> shChildObjects = new HashMap<String, List<String>>();

	public TurConverseAgentExchange importFromMultipartFile(MultipartFile multipartFile)
			throws IllegalStateException, IOException {
		File extractFolder = this.extractZipFile(multipartFile);
		File parentExtractFolder = null;

		if (extractFolder != null) {
			// Check if agent.json exists, if it is not exist try access a sub directory
			if (!(new File(extractFolder, "agent.json").exists()) && (extractFolder.listFiles().length == 1)) {
				for (File fileOrDirectory : extractFolder.listFiles()) {
					if (fileOrDirectory.isDirectory() && new File(fileOrDirectory, "agent.json").exists()) {
						parentExtractFolder = extractFolder;
						extractFolder = fileOrDirectory;
					}
				}
			}
			ObjectMapper mapper = new ObjectMapper();
			TurConverseAgentExchange turConverseAgentExchange = null;

			turConverseAgentExchange = mapper.readValue(
					new FileInputStream(extractFolder.getAbsolutePath().concat(File.separator + "agent.json")),
					TurConverseAgentExchange.class);
			System.out.println(turConverseAgentExchange.getDescription());

			TurConverseAgent turConverseAgent = new TurConverseAgent();
			if (multipartFile.getOriginalFilename() != null) {
				turConverseAgent.setName(multipartFile.getOriginalFilename().replace(".zip", ""));
			}
			turConverseAgent.setDescription(turConverseAgentExchange.getDescription());
			turConverseAgent.setCore("converse");
			turConverseAgent.setTurSEInstance(turSEInstanceRepository.findAll().get(0));
			if (turConverseAgentExchange.getLanguage().equals("pt-br")) {
				turConverseAgent.setLanguage("pt_BR");
			} else {
				turConverseAgent.setLanguage(turConverseAgentExchange.getLanguage());
			}

			turConverseAgentRepository.save(turConverseAgent);

			TurConverseEntityExchange turConverseEntityExchange = mapper.readValue(
					new FileInputStream(extractFolder.getAbsolutePath()
							.concat(File.separator + "entities" + File.separator + "pessoa.json")),
					TurConverseEntityExchange.class);

			System.out.println(turConverseEntityExchange.getName());

			TurConverseEntityEntriesExchange turConverseEntityEntriesExchange = mapper.readValue(
					new FileInputStream(extractFolder.getAbsolutePath()
							.concat(File.separator + "entities" + File.separator + "pessoa_entries_pt-br.json")),
					TurConverseEntityEntriesExchange.class);

			System.out.println(turConverseEntityEntriesExchange.get(0).getValue());

			final File folder = new File(extractFolder.getAbsolutePath().concat(File.separator + "intents"));

			listFilesForFolder(folder, turConverseAgent);

			try {
				FileUtils.deleteDirectory(extractFolder);
				if (parentExtractFolder != null) {
					FileUtils.deleteDirectory(parentExtractFolder);
				}
			} catch (IOException e) {
				logger.error("importFromMultipartFileException", e);
			}
			return turConverseAgentExchange;
		} else {
			return null;
		}
	}

	public void listFilesForFolder(final File folder, TurConverseAgent turConverseAgent) {

		final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

		for (final File fileEntry : folder.listFiles()) {

			try {
				System.out.println(fileEntry.getName());
				if (fileEntry.getName().contains("_usersays_")) {
					TurConverseIntentPhrasesExchange turConverseIntentPhrasesExchange = mapper
							.readValue(new FileInputStream(fileEntry), TurConverseIntentPhrasesExchange.class);
					System.out.println(turConverseIntentPhrasesExchange.get(0).getId());
				} else {

					TurConverseIntentExchange turConverseIntentExchange = mapper
							.readValue(new FileInputStream(fileEntry), TurConverseIntentExchange.class);
					System.out.println(turConverseIntentExchange.getName());

					// Intent
					TurConverseIntent turConverseIntent = new TurConverseIntent();
					turConverseIntent.setName(turConverseIntentExchange.getName());
					turConverseIntent.setAgent(turConverseAgent);
//					turConverseIntent.setActionName();
					turConverseIntentRepository.save(turConverseIntent);

					// Responses
					for (TurConverseIntentResponseExchange response : turConverseIntentExchange.getResponses()) {
						for (TurConverseIntentMessageExchange message : response.getMessages()) {
							if (message.getSpeech().size() > 0) {
								TurConverseResponse turConverseResponse = new TurConverseResponse(
										message.getSpeech().get(0));
								turConverseResponse.setIntent(turConverseIntent);
								turConverseResponseRepository.save(turConverseResponse);
							}

						}
					}
					File phrasesFile = new File(fileEntry.getAbsolutePath().replace(".json", "_usersays_pt-br.json"));
					if (phrasesFile.exists()) {
						TurConverseIntentPhrasesExchange turConverseIntentPhrasesExchange = mapper
								.readValue(new FileInputStream(phrasesFile), TurConverseIntentPhrasesExchange.class);

						// Phrases
						for (TurConverseIntentPhraseExchange phrase : turConverseIntentPhrasesExchange) {
							for (TurConverseIntentPhraseDataExchange data : phrase.getData()) {
								TurConversePhrase turConversePhrase = new TurConversePhrase(data.getText());
								turConversePhrase.setIntent(turConverseIntent);
								turConversePhraseRepository.save(turConversePhrase);
							}
						}

					} else {
						System.out.println("Usersays not exists: " + phrasesFile.getAbsolutePath());
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
	}

	public TurConverseAgentExchange importFromFile(File file) throws IOException, IllegalStateException {

		FileInputStream input = new FileInputStream(file);
		MultipartFile multipartFile = new MockMultipartFile(file.getName(), IOUtils.toByteArray(input));

		return this.importFromMultipartFile(multipartFile);
	}

	public File extractZipFile(MultipartFile file) throws IllegalStateException, IOException {
		shObjects.clear();
		shChildObjects.clear();

		File userDir = new File(System.getProperty("user.dir"));
		if (userDir.exists() && userDir.isDirectory()) {
			File tmpDir = new File(userDir.getAbsolutePath().concat(File.separator + "store" + File.separator + "tmp"));
			if (!tmpDir.exists()) {
				tmpDir.mkdirs();
			}

			File zipFile = new File(tmpDir.getAbsolutePath()
					.concat(File.separator + "imp_" + file.getOriginalFilename() + UUID.randomUUID()));

			file.transferTo(zipFile);
			File extractFolder = new File(tmpDir.getAbsolutePath().concat(File.separator + "imp_" + UUID.randomUUID()));
			turUtils.unZipIt(zipFile, extractFolder);
			FileUtils.deleteQuietly(zipFile);
			return extractFolder;
		} else {
			return null;
		}
	}
}
