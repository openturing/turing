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
package com.viglet.turing.converse.exchange;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.converse.exchange.agent.TurConverseAgentExchange;
import com.viglet.turing.converse.exchange.entity.TurConverseEntityEntriesExchange;
import com.viglet.turing.converse.exchange.entity.TurConverseEntityExchange;
import com.viglet.turing.converse.exchange.intent.*;
import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseIntent;
import com.viglet.turing.persistence.model.converse.intent.TurConversePhrase;
import com.viglet.turing.persistence.model.converse.intent.TurConverseResponse;
import com.viglet.turing.persistence.repository.converse.TurConverseAgentRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseIntentRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConversePhraseRepository;
import com.viglet.turing.persistence.repository.converse.intent.TurConverseResponseRepository;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
@Slf4j
@Component
public class TurConverseImportExchange {
	private static final String AGENT_FILE = "agent.json";
	private final TurConverseAgentRepository turConverseAgentRepository;
	private final TurConverseIntentRepository turConverseIntentRepository;
	private final TurConversePhraseRepository turConversePhraseRepository;
	private final TurConverseResponseRepository turConverseResponseRepository;
	private final TurSEInstanceRepository turSEInstanceRepository;

	@Inject
	public TurConverseImportExchange(TurConverseAgentRepository turConverseAgentRepository,
									 TurConverseIntentRepository turConverseIntentRepository,
									 TurConversePhraseRepository turConversePhraseRepository,
									 TurConverseResponseRepository turConverseResponseRepository,
									 TurSEInstanceRepository turSEInstanceRepository) {
		this.turConverseAgentRepository = turConverseAgentRepository;
		this.turConverseIntentRepository = turConverseIntentRepository;
		this.turConversePhraseRepository = turConversePhraseRepository;
		this.turConverseResponseRepository = turConverseResponseRepository;
		this.turSEInstanceRepository = turSEInstanceRepository;
	}

	public TurConverseAgentExchange importFromMultipartFile(@Nonnull MultipartFile multipartFile) {
		File extractFolder = this.extractZipFile(multipartFile);
		File parentExtractFolder = null;

		if (extractFolder != null) {
			// Check if agent.json exists, if it is not exist try access a sub directory
			if (!(new File(extractFolder, AGENT_FILE).exists()) && (Objects.requireNonNull(extractFolder.listFiles()).length == 1)) {
				for (File fileOrDirectory : Objects.requireNonNull(extractFolder.listFiles())) {
					if (fileOrDirectory.isDirectory() && new File(fileOrDirectory, AGENT_FILE).exists()) {
						parentExtractFolder = extractFolder;
						extractFolder = fileOrDirectory;
					}
				}
			}
			ObjectMapper mapper = new ObjectMapper();
			TurConverseAgentExchange turConverseAgentExchange = extractAgentExchange(extractFolder, mapper);

			TurConverseAgent turConverseAgent = saveConverseAgent(multipartFile, turConverseAgentExchange);

			extractEntityExchange(extractFolder, mapper);

			extractEntityEntriesExchange(extractFolder, mapper);

			final File folder = new File(extractFolder.getAbsolutePath().concat(File.separator + "intents"));

			listFilesForFolder(folder, turConverseAgent);

			deleteExtractFolder(extractFolder, parentExtractFolder);
			return turConverseAgentExchange;
		} else {
			return null;
		}
	}

	private void extractEntityEntriesExchange(File extractFolder, ObjectMapper mapper) {
		TurConverseEntityEntriesExchange turConverseEntityEntriesExchange;
		try {
			turConverseEntityEntriesExchange = mapper.readValue(
					new FileInputStream(extractFolder.getAbsolutePath()
							.concat(File.separator + "entities" + File.separator + "pessoa_entries_pt-br.json")),
					TurConverseEntityEntriesExchange.class);
			log.debug(turConverseEntityEntriesExchange.get(0).getValue());
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}

	}

	private void extractEntityExchange(File extractFolder, ObjectMapper mapper) {
		TurConverseEntityExchange turConverseEntityExchange;
		try {
			turConverseEntityExchange = mapper.readValue(
					new FileInputStream(extractFolder.getAbsolutePath()
							.concat(File.separator + "entities" + File.separator + "pessoa.json")),
					TurConverseEntityExchange.class);
			log.debug(turConverseEntityExchange.getName());
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}

	}

	private TurConverseAgent saveConverseAgent(MultipartFile multipartFile,
			TurConverseAgentExchange turConverseAgentExchange) {
		TurConverseAgent turConverseAgent = new TurConverseAgent();
		if (multipartFile.getOriginalFilename() != null && turConverseAgentExchange != null) {
			turConverseAgent.setName(multipartFile.getOriginalFilename().replace(".zip", "")); // NOSONAR
			turConverseAgent.setDescription(turConverseAgentExchange.getDescription());
			turConverseAgent.setCore("converse");
			turConverseAgent.setTurSEInstance(turSEInstanceRepository.findAll().get(0));
			if (turConverseAgentExchange.getLanguage().equals("pt-br")) {
				turConverseAgent.setLanguage("pt_BR");
			} else {
				turConverseAgent.setLanguage(turConverseAgentExchange.getLanguage());
			}
		
		turConverseAgentRepository.save(turConverseAgent);
		}
		return turConverseAgent;
	}

	private TurConverseAgentExchange extractAgentExchange(File extractFolder, ObjectMapper mapper) {
		TurConverseAgentExchange turConverseAgentExchange = null;

		try {
			turConverseAgentExchange = mapper.readValue(
					new FileInputStream(extractFolder.getAbsolutePath().concat(File.separator + AGENT_FILE)),
					TurConverseAgentExchange.class);
			log.debug(turConverseAgentExchange.getDescription());
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}

		return turConverseAgentExchange;
	}

	private void deleteExtractFolder(File extractFolder, File parentExtractFolder) {
		try {
			FileUtils.deleteDirectory(extractFolder);
			if (parentExtractFolder != null) {
				FileUtils.deleteDirectory(parentExtractFolder);
			}
		} catch (IOException e) {
			log.error("importFromMultipartFileException", e);
		}
	}

	public void listFilesForFolder(final File folder, TurConverseAgent turConverseAgent) {

		final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);

		for (final File fileEntry : Objects.requireNonNull(folder.listFiles())) {
			readFileFromFolder(turConverseAgent, mapper, fileEntry);
		}
	}

	private void readFileFromFolder(TurConverseAgent turConverseAgent, final ObjectMapper mapper,
			final File fileEntry) {
		log.debug(fileEntry.getName());
		if (fileEntry.getName().contains("_usersays_")) {
			extractIntentPhrasesExchange(mapper, fileEntry);
		} else {
			TurConverseIntentExchange turConverseIntentExchange = extractIntentExchange(mapper, fileEntry);
			TurConverseIntent turConverseIntent = saveIntent(turConverseAgent, turConverseIntentExchange);
			saveResponses(turConverseIntentExchange, turConverseIntent);
			savePhrases(mapper, fileEntry, turConverseIntent);
		}
	}

	private TurConverseIntentExchange extractIntentExchange(final ObjectMapper mapper, final File fileEntry) {
		TurConverseIntentExchange turConverseIntentExchange = new TurConverseIntentExchange();
		try {
			turConverseIntentExchange = mapper.readValue(new FileInputStream(fileEntry),
					TurConverseIntentExchange.class);
			log.debug(turConverseIntentExchange.getName());
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}

		return turConverseIntentExchange;
	}

	private void extractIntentPhrasesExchange(final ObjectMapper mapper, final File fileEntry) {
		TurConverseIntentPhrasesExchange turConverseIntentPhrasesExchange;
		try {
			turConverseIntentPhrasesExchange = mapper.readValue(new FileInputStream(fileEntry),
					TurConverseIntentPhrasesExchange.class);
			log.debug(turConverseIntentPhrasesExchange.get(0).getId());
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}

	}

	private void savePhrases(final ObjectMapper mapper, final File fileEntry, TurConverseIntent turConverseIntent) {
		File phrasesFile = new File(fileEntry.getAbsolutePath().replace(".json", "_usersays_pt-br.json"));
		if (phrasesFile.exists()) {
			TurConverseIntentPhrasesExchange turConverseIntentPhrasesExchange;
			try {
				turConverseIntentPhrasesExchange = mapper.readValue(new FileInputStream(phrasesFile),
						TurConverseIntentPhrasesExchange.class);
				for (TurConverseIntentPhraseExchange phrase : turConverseIntentPhrasesExchange) {
					for (TurConverseIntentPhraseDataExchange data : phrase.getData()) {
						TurConversePhrase turConversePhrase = new TurConversePhrase(data.getText());
						turConversePhrase.setIntent(turConverseIntent);
						turConversePhraseRepository.save(turConversePhrase);
					}
				}
			} catch (IOException e) {
				log.error(e.getMessage(), e);
			}

		} else {
			log.debug("Usersays not exists: {}", phrasesFile.getAbsolutePath());
		}
	}

	private void saveResponses(TurConverseIntentExchange turConverseIntentExchange,
			TurConverseIntent turConverseIntent) {
		for (TurConverseIntentResponseExchange response : turConverseIntentExchange.getResponses()) {
			for (TurConverseIntentMessageExchange message : response.getMessages()) {
				if (!message.getSpeech().isEmpty()) {
					TurConverseResponse turConverseResponse = new TurConverseResponse(message.getSpeech().get(0));
					turConverseResponse.setIntent(turConverseIntent);
					turConverseResponseRepository.save(turConverseResponse);
				}

			}
		}
	}

	private TurConverseIntent saveIntent(TurConverseAgent turConverseAgent,
			TurConverseIntentExchange turConverseIntentExchange) {
		TurConverseIntent turConverseIntent = new TurConverseIntent();
		turConverseIntent.setName(turConverseIntentExchange.getName());
		turConverseIntent.setAgent(turConverseAgent);
		turConverseIntentRepository.save(turConverseIntent);
		return turConverseIntent;
	}

	public TurConverseAgentExchange importFromFile(File file) {

		try (FileInputStream input = new FileInputStream(file)) {
			MultipartFile multipartFile = new MockMultipartFile(file.getName(), IOUtils.toByteArray(input));
			return this.importFromMultipartFile(multipartFile);
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		return new TurConverseAgentExchange();
	}

	public File extractZipFile(MultipartFile file) {
		return TurCommonsUtils.extractZipFile(file);
	}

}
