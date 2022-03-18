/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

package com.viglet.turing.exchange.sn;

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

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.utils.TurUtils;

@Component
public class TurSNSiteExport {
	private static final Log logger = LogFactory.getLog(MethodHandles.lookup().lookupClass());
	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurUtils turUtils;

	public StreamingResponseBody exportObject(HttpServletResponse response) {
		String folderName = UUID.randomUUID().toString();
		File userDir = new File(System.getProperty("user.dir"));
		if (userDir.exists() && userDir.isDirectory()) {
			File tmpDir = new File(userDir.getAbsolutePath().concat(File.separator + "store" + File.separator + "tmp"));
			if (!tmpDir.exists()) {
				tmpDir.mkdirs();
			}

			List<TurSNSite> turSNSites = turSNSiteRepository.findAll();

			List<TurSNSiteExchange> shSiteExchanges = new ArrayList<>();

			for (TurSNSite turSNSite : turSNSites) {
				shSiteExchanges.add(this.exportSNSite(turSNSite));
			}

			File exportDir = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName));
			if (!exportDir.exists()) {
				exportDir.mkdirs();
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

				turUtils.addFilesToZip(exportDir, zipFile);

				String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
				String zipFileName = "SNSite_" + strDate + ".zip";

				response.addHeader("Content-disposition", "attachment;filename=" + zipFileName);
				response.setContentType("application/octet-stream");
				response.setStatus(HttpServletResponse.SC_OK);

				return new StreamingResponseBody() {
					@Override
					public void writeTo(OutputStream output) throws IOException {

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
