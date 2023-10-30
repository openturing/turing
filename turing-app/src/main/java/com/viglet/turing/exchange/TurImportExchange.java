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

package com.viglet.turing.exchange;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.exchange.sn.TurSNSiteImport;
import com.viglet.turing.utils.TurUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;
@Slf4j
@Component
public class TurImportExchange {

	@Autowired
	private TurSNSiteImport turSNSiteImport;
	private static final String EXPORT_FILE = "export.json";
	public TurExchange importFromMultipartFile(MultipartFile multipartFile) {
		File extractFolder = this.extractZipFile(multipartFile);
		File parentExtractFolder = null;

		if (extractFolder != null) {
			// Check if export.json exists, if it is not exist try access a sub directory
			if (!(new File(extractFolder, EXPORT_FILE).exists()) && (Objects.requireNonNull(extractFolder.listFiles()).length == 1)) {
				for (File fileOrDirectory : Objects.requireNonNull(extractFolder.listFiles())) {
					if (fileOrDirectory.isDirectory() && new File(fileOrDirectory, EXPORT_FILE).exists()) {
						parentExtractFolder = extractFolder;
						extractFolder = fileOrDirectory;
					}
				}
			}
			importSNSiteFromExportFile(extractFolder, parentExtractFolder);
		}
		return new TurExchange();
	}
	private void importSNSiteFromExportFile(File extractFolder, File parentExtractFolder) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			TurExchange turExchange = mapper.readValue(
					new FileInputStream(extractFolder.getAbsolutePath().concat(File.separator).concat(EXPORT_FILE)),
					TurExchange.class);

			if (turExchange.getSnSites() != null && !turExchange.getSnSites().isEmpty()) {
				turSNSiteImport.importSNSite(turExchange);
			}

			FileUtils.deleteDirectory(extractFolder);
			if (parentExtractFolder != null) {
				FileUtils.deleteDirectory(parentExtractFolder);
			}

			return;
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
		new TurExchange();
	}

	public TurExchange importFromFile(File file) {

		try (FileInputStream input = new FileInputStream(file)) {
			MultipartFile multipartFile = new MockMultipartFile(file.getName(), IOUtils.toByteArray(input));
			return this.importFromMultipartFile(multipartFile);
		} catch (IOException | IllegalStateException e) {
			log.error(e.getMessage(), e);
		}
		return new TurExchange();
	}

	public File extractZipFile(MultipartFile file) {
		return TurUtils.extractZipFile(file);
	}
}
