package com.viglet.turing.exchange.sn;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.util.TurUtils;

@Component
public class TurSNSiteExport {
	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurUtils turUtils;
	
	public StreamingResponseBody exportObject(HttpServletResponse response) throws Exception {
		String folderName = UUID.randomUUID().toString();
		File userDir = new File(System.getProperty("user.dir"));
		if (userDir.exists() && userDir.isDirectory()) {
			File tmpDir = new File(userDir.getAbsolutePath().concat(File.separator + "store" + File.separator + "tmp"));
			if (!tmpDir.exists()) {
				tmpDir.mkdirs();
			}

			List<TurSNSite> turSNSites = turSNSiteRepository.findAll();

			List<TurSNSiteExchange> shSiteExchanges = new ArrayList<TurSNSiteExchange>();

			for (TurSNSite turSNSite : turSNSites) {
				shSiteExchanges.add(this.exportSNSite(turSNSite));
			}

			File exportDir = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName));
			if (!exportDir.exists()) {
				exportDir.mkdirs();
			}

			TurExchange turExchange = new TurExchange();
			if (shSiteExchanges.size() > 0) {
				turExchange.setSnSites(shSiteExchanges);
			}
			// Object to JSON in file
			ObjectMapper mapper = new ObjectMapper();
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
				public void writeTo(java.io.OutputStream output) throws IOException {

					try {
						java.nio.file.Path path = Paths.get(zipFile.getAbsolutePath());
						byte[] data = Files.readAllBytes(path);
						output.write(data);
						output.flush();

						FileUtils.deleteDirectory(exportDir);
						FileUtils.deleteQuietly(zipFile);

					} catch (IOException ex) {
						ex.printStackTrace();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			};
		} else {
			return null;
		}
	}

	public TurSNSiteExchange exportSNSite(TurSNSite turSNSite) {
		TurSNSiteExchange turSNSiteExchange = new TurSNSiteExchange(turSNSite);

		return turSNSiteExchange;
	}
}
