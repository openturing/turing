package com.viglet.turing.connector.sprinklr.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.sprinklr.export.bean.TurSprinklrAttribExchange;
import com.viglet.turing.connector.sprinklr.export.bean.TurSprinklrExchange;
import com.viglet.turing.connector.sprinklr.export.bean.TurSprinklrSourceExchange;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrAttributeMapping;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrSource;
import com.viglet.turing.connector.sprinklr.persistence.repository.TurSprinklrAttributeMappingRepository;
import com.viglet.turing.connector.sprinklr.persistence.repository.TurSprinklrSourceRepository;
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
public class TurSprinklrExchangeProcess {
    private static final String EXPORT_FILE = "export.json";
    private final TurSprinklrSourceRepository turSprinklrSourceRepository;
    private final TurSprinklrAttributeMappingRepository turSprinklrAttributeMappingRepository;

    @Inject
    public TurSprinklrExchangeProcess(TurSprinklrSourceRepository turSprinklrSourceRepository,
                                      TurSprinklrAttributeMappingRepository turSprinklrAttributeMappingRepository) {
        this.turSprinklrSourceRepository = turSprinklrSourceRepository;
        this.turSprinklrAttributeMappingRepository = turSprinklrAttributeMappingRepository;
    }

    private Collection<TurSprinklrAttribExchange> attributeExchange(Collection<TurSprinklrAttributeMapping> attributeMappings) {
        Collection<TurSprinklrAttribExchange> attribExchanges = new ArrayList<>();
        attributeMappings.forEach(attributeMapping -> attribExchanges.add(TurSprinklrAttribExchange.builder()
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

            List<TurSprinklrSource> turSprinklrSources = turSprinklrSourceRepository.findAll();


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
                        new TurSprinklrExchange(turSprinklrSources.stream()
                                .map(turSprinklrSource -> TurSprinklrSourceExchange.builder()
                                        .id(turSprinklrSource.getId())
                                        .url(turSprinklrSource.getUrl())
                                        .attributes(attributeExchange(turSprinklrSource.getAttributeMappings()))
                                        .locale(turSprinklrSource.getLocale())
                                        .localeClass(turSprinklrSource.getLocaleClass())
                                        .turSNSites(turSprinklrSource.getTurSNSites())
                                        .build()).toList()));

                File zipFile = new File(tmpDir.getAbsolutePath().concat(File.separator + folderName + ".zip"));

                TurCommonsUtils.addFilesToZip(exportDir, zipFile);

                String strDate = new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date());
                String zipFileName = "Sprinklr_" + strDate + ".zip";

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
            TurSprinklrExchange turSprinklrExchange = mapper.readValue(exportFile, TurSprinklrExchange.class);
            if (turSprinklrExchange.getSources() != null && !turSprinklrExchange.getSources().isEmpty()) {
                importSprinklrSource(turSprinklrExchange);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void importSprinklrSource(TurSprinklrExchange turSprinklrExchange) {
        for (TurSprinklrSourceExchange turSprinklrSourceExchange : turSprinklrExchange.getSources()) {
            if (turSprinklrSourceRepository.findById(turSprinklrSourceExchange.getId()).isEmpty()) {
                TurSprinklrSource turSprinklrSource = TurSprinklrSource.builder()
                        .id(turSprinklrSourceExchange.getId())
                        .url(turSprinklrSourceExchange.getUrl())
                        .turSNSites(turSprinklrSourceExchange.getTurSNSites())
                        .locale(turSprinklrSourceExchange.getLocale())
                        .localeClass(turSprinklrSourceExchange.getLocaleClass())
                        .environment(turSprinklrSourceExchange.getEnvironment())
                        .authorizationCode(turSprinklrSourceExchange.getAuthorizationCode())
                        .apiKey(turSprinklrSourceExchange.getApiKey())
                        .build();

                turSprinklrSourceRepository.save(turSprinklrSource);

                turSprinklrSourceExchange.getAttributes().forEach(attribute ->
                        turSprinklrAttributeMappingRepository.save(TurSprinklrAttributeMapping.builder()
                                .name(attribute.getName())
                                .className(attribute.getClassName())
                                .text(attribute.getText())
                                .turSprinklrSource(turSprinklrSource)
                                .build()));
            }
        }
    }
}
