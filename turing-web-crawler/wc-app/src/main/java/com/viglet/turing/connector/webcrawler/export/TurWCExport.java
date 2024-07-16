package com.viglet.turing.connector.webcrawler.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.webcrawler.export.bean.TurWCAttribExchange;
import com.viglet.turing.connector.webcrawler.export.bean.TurWCSourceExchange;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCAttributeMapping;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCFileExtension;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCSource;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCUrl;
import com.viglet.turing.connector.webcrawler.persistence.repository.TurWCSourceRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

@Log4j2
@Component
public class TurWCExport {
    private final TurWCSourceRepository turWCSourceRepository;

    @Inject
    public TurWCExport(TurWCSourceRepository turWCSourceRepository) {
        this.turWCSourceRepository = turWCSourceRepository;
    }

    private Collection<TurWCAttribExchange> attributeExchange(Collection<TurWCAttributeMapping> attributeMappings) {
        Collection<TurWCAttribExchange> attribExchanges = new ArrayList<>();
        attributeMappings.forEach(attributeMapping -> attribExchanges.add(TurWCAttribExchange.builder()
                .name(attributeMapping.getName())
                .className(attributeMapping.getClassName())
                .text(attributeMapping.getText())
                .build()));
        return attribExchanges;
    }

    public StreamingResponseBody exportObject(HttpServletResponse response) {
        String folderName = UUID.randomUUID().toString();
        File userDir = new File(System.getProperty("user.dir"));
        if (userDir.exists() && userDir.isDirectory()) {
            File tmpDir = new File(userDir.getAbsolutePath().concat(File.separator + "store" + File.separator + "tmp"));
            try {
                Files.createDirectories(tmpDir.toPath());
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }

            List<TurWCSource> turWCSources = turWCSourceRepository.findAll();
            List<TurWCSourceExchange> turWCSourceExchanges = new ArrayList<>();
            turWCSources.forEach(turWCSource -> turWCSourceExchanges.add(TurWCSourceExchange.builder()
                    .id(turWCSource.getId())
                    .url(turWCSource.getUrl())
                    .allowUrls(turWCSource.getAllowUrls().stream().map(TurWCUrl::getUrl).toList())
                    .attributes(attributeExchange(turWCSource.getAttributeMappings()))
                    .locale(turWCSource.getLocale())
                    .password(turWCSource.getPassword())
                    .localeClass(turWCSource.getLocaleClass())
                    .turSNSite(turWCSource.getTurSNSite())
                    .username(turWCSource.getUsername())
                    .notAllowUrls(turWCSource.getNotAllowUrls().stream().map(TurWCUrl::getUrl).toList())
                    .notAllowExtensions(turWCSource.getNotAllowExtensions().stream()
                            .map(TurWCFileExtension::getExtension).toList())
                    .build()));
            File exportDir = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName));
            File exportFile = new File(exportDir.getAbsolutePath().concat(File.separator + "export.json"));
            try {
                Files.createDirectories(exportDir.toPath());
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }

            // Object to JSON in file
            ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(exportFile, turWCSourceExchanges);

                File zipFile = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName + ".zip"));

                TurCommonsUtils.addFilesToZip(exportDir, zipFile);

                String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
                String zipFileName = "WC_" + strDate + ".zip";

                response.addHeader("Content-disposition", "attachment;filename=" + zipFileName);
                response.setContentType("application/octet-stream");
                response.setStatus(HttpServletResponse.SC_OK);

                return output -> {

                    try {
                        java.nio.file.Path path = Paths.get(zipFile.getAbsolutePath());
                        byte[] data = Files.readAllBytes(path);
                        output.write(data);
                        output.flush();

                        FileUtils.deleteDirectory(exportDir);
                        FileUtils.deleteQuietly(zipFile);

                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                };
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        return null;
    }
}
