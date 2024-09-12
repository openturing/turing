/*
 *
 * Copyright (C) 2016-2024 the original author or authors. 
 *  
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.connector.aem.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.aem.export.bean.TurAemAttribExchange;
import com.viglet.turing.connector.aem.export.bean.TurAemExchange;
import com.viglet.turing.connector.aem.export.bean.TurAemSourceExchange;
import com.viglet.turing.connector.aem.persistence.model.TurAemAttributeMapping;
import com.viglet.turing.connector.aem.persistence.model.TurAemSource;
import com.viglet.turing.connector.aem.persistence.repository.TurAemAttributeMappingRepository;
import com.viglet.turing.connector.aem.persistence.repository.TurAemSourceRepository;
import com.viglet.turing.spring.utils.TurSpringUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Component
@Transactional
public class TurAemExchangeProcess {
    private static final String EXPORT_FILE = "export.json";
    private final TurAemSourceRepository turAemSourceRepository;
    private final TurAemAttributeMappingRepository turAemAttributeMappingRepository;

    @Inject
    public TurAemExchangeProcess(TurAemSourceRepository turAemSourceRepository,
                                      TurAemAttributeMappingRepository turAemAttributeMappingRepository) {
        this.turAemSourceRepository = turAemSourceRepository;
        this.turAemAttributeMappingRepository = turAemAttributeMappingRepository;
    }

    private Collection<TurAemAttribExchange> attributeExchange(Collection<TurAemAttributeMapping> attributeMappings) {
        Collection<TurAemAttribExchange> attribExchanges = new ArrayList<>();
        attributeMappings.forEach(attributeMapping -> attribExchanges.add(TurAemAttribExchange.builder()
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

            List<TurAemSource> turAemSources = turAemSourceRepository.findAll();


            File exportDir = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName));
            File exportFile = new File(exportDir.getAbsolutePath().concat(File.separator + EXPORT_FILE));
            try {
                Files.createDirectories(exportDir.toPath());
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }

            // Object to JSON in file
            ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
            try {
                mapper.writerWithDefaultPrettyPrinter().writeValue(exportFile,
                        new TurAemExchange(turAemSources.stream()
                                .map(turAemSource -> TurAemSourceExchange.builder()
                                        .id(turAemSource.getId())
                                        .url(turAemSource.getUrl())
                                        .attributes(attributeExchange(turAemSource.getAttributeMappings()))
                                        .locale(turAemSource.getLocale())
                                        .localeClass(turAemSource.getLocaleClass())
                                        .turSNSites(turAemSource.getTurSNSites())
                                        .build()).toList()));

                File zipFile = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName + ".zip"));

                TurCommonsUtils.addFilesToZip(exportDir, zipFile);

                String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
                String zipFileName = "Aem_" + strDate + ".zip";

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

    public void importFromMultipartFile(MultipartFile multipartFile) {
        File extractFolder = TurSpringUtils.extractZipFile(multipartFile);
        File parentExtractFolder = null;
        if (!(new File(extractFolder, EXPORT_FILE).exists()) && Objects.requireNonNull(extractFolder.listFiles()).length == 1) {
            for (File fileOrDirectory : Objects.requireNonNull(extractFolder.listFiles())) {
                if (fileOrDirectory.isDirectory() && new File(fileOrDirectory, EXPORT_FILE).exists()) {
                    parentExtractFolder = extractFolder;
                    extractFolder = fileOrDirectory;
                }
            }
        }
        File exportFile = new File(extractFolder.getAbsolutePath().concat(File.separator + EXPORT_FILE));
        importFromFile(exportFile);
        try {
            FileUtils.deleteDirectory(extractFolder);
            if (parentExtractFolder != null) {
                FileUtils.deleteDirectory(parentExtractFolder);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

    }

    public void importFromFile(File exportFile) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            TurAemExchange turAemExchange = mapper.readValue(exportFile, TurAemExchange.class);
            if (turAemExchange.getSources() != null && !turAemExchange.getSources().isEmpty()) {
                importAemSource(turAemExchange);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void importAemSource(TurAemExchange turAemExchange) {
        for (TurAemSourceExchange turAemSourceExchange : turAemExchange.getSources()) {
            if (turAemSourceRepository.findById(turAemSourceExchange.getId()).isEmpty()) {
                TurAemSource turAemSource = TurAemSource.builder()
                        .id(turAemSourceExchange.getId())
                        .url(turAemSourceExchange.getUrl())
                        .turSNSites(turAemSourceExchange.getTurSNSites())
                        .locale(turAemSourceExchange.getLocale())
                        .localeClass(turAemSourceExchange.getLocaleClass())
                        .build();

                turAemSourceRepository.save(turAemSource);

                turAemSourceExchange.getAttributes().forEach(attribute ->
                        turAemAttributeMappingRepository.save(TurAemAttributeMapping.builder()
                                .name(attribute.getName())
                                .className(attribute.getClassName())
                                .text(attribute.getText())
                                .turAemSource(turAemSource)
                                .build()));
            }
        }
    }
}
