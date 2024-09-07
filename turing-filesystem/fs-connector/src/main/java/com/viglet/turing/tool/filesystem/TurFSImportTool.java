package com.viglet.turing.tool.filesystem;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.LocaleUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.viglet.turing.client.sn.TurSNConstants.ID_ATTR;

@Slf4j
public class TurFSImportTool {
    @Parameter(names = "--source-dir", description = "Source Directory that contains files", required = true)
    private String sourceDir = null;

    @Parameter(names = "--prefix-from-replace", description = "Prefix from Replace")
    private String prefixFromReplace = null;

    @Parameter(names = "--prefix-to-replace", description = "Prefix to Replace")
    private String prefixToReplace = null;

    @Parameter(names = {"--server", "-s"}, description = "Viglet Turing Server", required = true)
    private String turingServer = "http://localhost:2700";

    @Parameter(names = {"--api-key", "-a"}, description = "Viglet Turing API Key", required = true)
    private String turingApiKey = null;

    @Parameter(names = {"--site"}, description = "Specify the Semantic Navigation Site", required = true)
    private String site = null;

    @Parameter(names = {"--locale"}, description = "Specify the Semantic Navigation Site Locale")
    private String locale = "en_US";

    @Parameter(names = {"--type", "-t"}, description = "Set Content Type name")
    public String type = "Static File";

    @Parameter(names = {"--chunk", "-z"}, description = "Number of items to be sent to the queue")
    private int chunk = 100;

    @Parameter(names = {"--include-type-in-id", "-i"}, description = "Include Content Type name in Id", arity = 1)
    public boolean typeInId = false;

    @Parameter(names = "--file-size-field", description = "Field that shows Size of File in bytes", help = true)
    private String fileSizeField = "fileSize";

    @Parameter(names = "--file-extension-field", description = "Field that shows extension of File", help = true)
    private String fileExtensionField = "fileExtension";

    @Parameter(names = {"--show-output", "-o"}, description = "Show Output", arity = 1)
    public boolean showOutput = false;

    @Parameter(names = {"--encoding"}, description = "Encoding Source")
    public String encoding = "UTF-8";

    @Parameter(names = "--help", description = "Print usage instructions", help = true)
    private boolean help = false;

    public static void main(String... argv) {

        TurFSImportTool main = new TurFSImportTool();
        JCommander jCommander = JCommander.newBuilder().addObject(main).build();
        try {
            jCommander.parse(argv);
            if (main.help) {
                jCommander.usage();
                return;
            }
            System.out.println("Viglet Turing Filesystem Import Tool.");
            main.run();
        } catch (ParameterException e) {
            // Handle everything on your own, i.e.
            log.info("Error: {}", e.getLocalizedMessage());
            jCommander.usage();
        }

    }

    public void run() {
        TurFSChunkingJob turChunkingJob = new TurFSChunkingJob(chunk);
        Path startPath = Paths.get(sourceDir);
        long totalFiles = Objects.requireNonNull(startPath.toFile().list()).length;
        try {
            Files.walkFileTree(startPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                            File file = new File(path.toAbsolutePath().toString());
                            log.info("Process file: {}", file.getAbsolutePath());

                            turChunkingJob.addItem( createJobItem(file));
                            if (turChunkingJob.isChunkLimit()) {
                                sendToTuring(turChunkingJob, totalFiles);
                                turChunkingJob.newCicle();
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
            if (turChunkingJob.hasItemsLeft()) {
                this.sendToTuring(turChunkingJob, totalFiles);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

    }

    private TurSNJobItem createJobItem(File file) {
        TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.CREATE, Collections.singletonList(this.site),
                LocaleUtils.toLocale(locale));
        Map<String, Object> attributes = new HashMap<>();

        List<String> webImagesExtensions = new ArrayList<>(
                Arrays.asList("pnm", "png", "jpg", "jpeg", "gif"));
        String extension = FilenameUtils.getExtension(file.getAbsolutePath()).toLowerCase();
        if (!extension.equals("ds_store")) {

            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            df.setTimeZone(tz);
            String fileURL = file.getAbsolutePath();
            if (prefixFromReplace != null && prefixToReplace != null)
                fileURL = fileURL.replace(prefixFromReplace, prefixToReplace);
            if (typeInId)
                attributes.put("id", type + fileURL);
            else
                attributes.put("id", fileURL);
            attributes.put("date", df.format(file.lastModified()));
            attributes.put("title", file.getName());
            attributes.put("type", type);
            if (webImagesExtensions.contains(extension))
                attributes.put("image", fileURL);
            if (fileExtensionField != null)
                attributes.put(fileExtensionField, extension);
            if (fileSizeField != null)
                attributes.put(fileSizeField, file.length());
            attributes.put("url", fileURL);
            try {
                attributes.put("text", TurOcr.processFile(new TurServer(URI.create(turingServer).toURL(),
                        new TurApiKeyCredentials(turingApiKey)), file, showOutput));
            } catch (MalformedURLException e) {
               log.error(e.getMessage(), e);
            }
            turSNJobItem.setAttributes(attributes);

           return turSNJobItem;
        }
        return null;
    }

    private void sendToTuring(TurFSChunkingJob turChunkingJob, long totalFiles) {
        log.info("Send job to Turing");
        if (log.isDebugEnabled()) {
            for (TurSNJobItem turSNJobItem : turChunkingJob.getTurSNJobItems()) {
                log.debug("TurSNJobItem Id: {}", turSNJobItem.getAttributes().get(ID_ATTR));
            }
        }
        System.out.printf("Importing %s to %s of %s items%n", turChunkingJob.getFirstItemPosition(),
                turChunkingJob.getTotal(), totalFiles);
        try {
            TurSNJobUtils.importItems(turChunkingJob.getTurSNJobItems(),
                    new TurSNServer(URI.create(turingServer).toURL(), null,
                            new TurApiKeyCredentials(turingApiKey)),
                    showOutput);
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }

    }
}
