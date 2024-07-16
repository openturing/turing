package com.viglet.turing.connector.webcrawler.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.webcrawler.export.bean.TurWCAttribExchange;
import com.viglet.turing.connector.webcrawler.export.bean.TurWCExchange;
import com.viglet.turing.connector.webcrawler.export.bean.TurWCSourceExchange;
import com.viglet.turing.connector.webcrawler.persistence.model.*;
import com.viglet.turing.connector.webcrawler.persistence.repository.*;
import com.viglet.turing.spring.utils.TurSpringUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
@Component
@Transactional
public class TurWCExchangeProcess {
    private static final String EXPORT_FILE = "export.json";
    private final TurWCSourceRepository turWCSourceRepository;
    private final TurWCAllowUrlRepository turWCAllowUrlRepository;
    private final TurWCNotAllowUrlRepository turWCNotAllowUrlRepository;
    private final TurWCFileExtensionRepository turWCFileExtensionRepository;
    private final TurWCAttributeMappingRepository turWCAttributeMappingRepository;

    @Inject
    public TurWCExchangeProcess(TurWCSourceRepository turWCSourceRepository,
                                TurWCAllowUrlRepository turWCAllowUrlRepository,
                                TurWCNotAllowUrlRepository turWCNotAllowUrlRepository,
                                TurWCFileExtensionRepository turWCFileExtensionRepository,
                                TurWCAttributeMappingRepository turWCAttributeMappingRepository) {
        this.turWCSourceRepository = turWCSourceRepository;
        this.turWCAllowUrlRepository = turWCAllowUrlRepository;
        this.turWCNotAllowUrlRepository = turWCNotAllowUrlRepository;
        this.turWCFileExtensionRepository = turWCFileExtensionRepository;
        this.turWCAttributeMappingRepository = turWCAttributeMappingRepository;
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
                        new TurWCExchange(turWCSources.stream()
                                .map(turWCSource -> TurWCSourceExchange.builder()
                                        .id(turWCSource.getId())
                                        .url(turWCSource.getUrl())
                                        .allowUrls(turWCSource.getAllowUrls().stream().map(TurWCUrl::getUrl)
                                                .toList())
                                        .attributes(attributeExchange(turWCSource.getAttributeMappings()))
                                        .locale(turWCSource.getLocale())
                                        .password(turWCSource.getPassword())
                                        .localeClass(turWCSource.getLocaleClass())
                                        .turSNSite(turWCSource.getTurSNSite())
                                        .username(turWCSource.getUsername())
                                        .notAllowUrls(turWCSource.getNotAllowUrls().stream().map(TurWCUrl::getUrl)
                                                .toList())
                                        .notAllowExtensions(turWCSource.getNotAllowExtensions().stream()
                                                .map(TurWCFileExtension::getExtension).toList())
                                        .build()).toList()));

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
        ObjectMapper mapper = new ObjectMapper();
        try {
            TurWCExchange turWCExchange = mapper.readValue(
                    new FileInputStream(extractFolder.getAbsolutePath().concat(File.separator).concat(EXPORT_FILE)),
                    TurWCExchange.class);

            if (turWCExchange.getSources() != null && !turWCExchange.getSources().isEmpty()) {
                importWCSource(turWCExchange);
            }

            FileUtils.deleteDirectory(extractFolder);
            if (parentExtractFolder != null) {
                FileUtils.deleteDirectory(parentExtractFolder);
            }

        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void importWCSource(TurWCExchange turWCExchange) {
        for (TurWCSourceExchange turWCSourceExchange : turWCExchange.getSources()) {
            if (turWCSourceRepository.findById(turWCSourceExchange.getId()).isEmpty()) {
                TurWCSource turWCSource = TurWCSource.builder()
                        .id(turWCSourceExchange.getId())
                        .url(turWCSourceExchange.getUrl())
                        .username(turWCSourceExchange.getUsername())
                        .password(turWCSourceExchange.getPassword())
                        .turSNSite(turWCSourceExchange.getTurSNSite())
                        .locale(turWCSourceExchange.getLocale())
                        .localeClass(turWCSourceExchange.getLocaleClass())
                        .build();

                turWCSourceRepository.save(turWCSource);

                turWCSourceExchange.getAllowUrls().forEach(url ->
                        turWCAllowUrlRepository.save(TurWCAllowUrl.builder()
                                .url(url)
                                .turWCSource(turWCSource)
                                .build()));

                turWCSourceExchange.getNotAllowUrls().forEach(url ->
                        turWCNotAllowUrlRepository.save(TurWCNotAllowUrl.builder()
                                .url(url)
                                .turWCSource(turWCSource)
                                .build()));

                turWCSourceExchange.getNotAllowExtensions().forEach(extension ->
                        turWCFileExtensionRepository.save(TurWCFileExtension.builder()
                                .extension(extension)
                                .turWCSource(turWCSource)
                                .build()));

                turWCSourceExchange.getAttributes().forEach(attribute ->
                        turWCAttributeMappingRepository.save(TurWCAttributeMapping.builder()
                                .name(attribute.getName())
                                .className(attribute.getClassName())
                                .text(attribute.getText())
                                .turWCSource(turWCSource)
                                .build()));
            }
        }
    }
}
