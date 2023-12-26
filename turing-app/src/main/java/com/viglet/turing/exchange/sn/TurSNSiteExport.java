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

package com.viglet.turing.exchange.sn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class TurSNSiteExport {
	private static final Log logger = LogFactory.getLog(MethodHandles.lookup().lookupClass());

	private final TurSNSiteRepository turSNSiteRepository;

	@Inject
	public TurSNSiteExport(TurSNSiteRepository turSNSiteRepository) {
		this.turSNSiteRepository = turSNSiteRepository;
	}

	public StreamingResponseBody exportObject(HttpServletResponse response) {
		String folderName = UUID.randomUUID().toString();
		File userDir = new File(System.getProperty("user.dir"));
		if (userDir.exists() && userDir.isDirectory()) {
			File tmpDir = new File(userDir.getAbsolutePath().concat(File.separator + "store" + File.separator + "tmp"));
			try {
				Files.createDirectories(tmpDir.toPath());
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}

			List<TurSNSite> turSNSites = turSNSiteRepository.findAll();

			List<TurSNSiteExchange> shSiteExchanges = new ArrayList<>();

			for (TurSNSite turSNSite : turSNSites) {
				shSiteExchanges.add(this.exportSNSite(turSNSite));
			}

			File exportDir = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName));
			try {
				Files.createDirectories(tmpDir.toPath());
			} catch (IOException e) {
				logger.error(e.getMessage(), e);
			}

			TurExchange turExchange = new TurExchange();
			if (!shSiteExchanges.isEmpty()) {
				turExchange.setSnSites(shSiteExchanges);
			}
			// Object to JSON in file
			ObjectMapper mapper = new ObjectMapper();
			try {
				mapper.writerWithDefaultPrettyPrinter().writeValue(
						new File(exportDir.getAbsolutePath().concat(File.separator + "export.json")), turExchange);

				File zipFile = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName + ".zip"));

				TurCommonsUtils.addFilesToZip(exportDir, zipFile);

				String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
				String zipFileName = "SNSite_" + strDate + ".zip";

				response.addHeader("Content-disposition", "attachment;filename=" + zipFileName);
				response.setContentType("application/octet-stream");
				response.setStatus(HttpServletResponse.SC_OK);

				return new StreamingResponseBody() {
					@Override
					public void writeTo(@NotNull OutputStream output) throws IOException {

						try {
							java.nio.file.Path path = Paths.get(zipFile.getAbsolutePath());
							byte[] data = Files.readAllBytes(path);
							output.write(data);
							output.flush();

							FileUtils.deleteDirectory(exportDir);
							FileUtils.deleteQuietly(zipFile);

						} catch (Exception e) {
							logger.error(e.getMessage(), e);
						}
					}
				};
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		} 
		return null;
	}

	public TurSNSiteExchange exportSNSite(TurSNSite turSNSite) {
		return new TurSNSiteExchange(turSNSite);
	}
}
