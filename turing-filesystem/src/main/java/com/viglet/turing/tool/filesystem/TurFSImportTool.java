package com.viglet.turing.tool.filesystem;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TurFSImportTool {
    static final Logger logger = LogManager.getLogger(TurFSImportTool.class.getName());

    int chunkCurrent = 0;
    int chunkTotal = 0;
    TurSNJobItems turSNJobItems = new TurSNJobItems();
    @Parameter(names = "--source-dir", description = "Source Directory that contains files", required = true)
    private String sourceDir = null;

    @Parameter(names = "--prefix-from-replace", description = "Prefix from Replace", required = false)
    private String prefixFromReplace = null;

    @Parameter(names = "--prefix-to-replace", description = "Prefix to Replace", required = false)
    private String prefixToReplace = null;

    @Parameter(names = {"--site"}, description = "Specify the Semantic Navigation Site", required = false)
    private String site = null;

    @Parameter(names = {"--nlp"}, description = "Specify the NLP Instance", required = false)
    private String nlpInstance = null;

    @Parameter(names = {"--server", "-s"}, description = "Viglet Turing Server", required = true)
    private String turingServer = "http://localhost:2700";

    @Parameter(names = {"--type", "-t"}, description = "Set Content Type name")
    public String type = "CONTENT_TYPE";

    @Parameter(names = {"--chunk", "-z"}, description = "Number of items to be sent to the queue")
    private int chunk = 100;

    @Parameter(names = {"--include-type-in-id", "-i"}, description = "Include Content Type name in Id", arity = 1)
    public boolean typeInId = false;

    @Parameter(names = "--file-content-field", description = "Field that shows Content of File", help = true)
    private String fileContentField = "text";

    @Parameter(names = "--file-size-field", description = "Field that shows Size of File in bytes", help = true)
    private String fileSizeField = "fileSize";

    @Parameter(names = "--file-extension-field", description = "Field that shows extension of File", help = true)
    private String fileExtensionField = "fileExtension";

    @Parameter(names = {"--show-output", "-o"}, description = "Show Output", arity = 1)
    public boolean showOutput = false;

    @Parameter(names = {"--encoding"}, description = "Encoding Source")
    public String encoding = "UTF-8";

    @Parameter(names = {"--output-dir"}, description = "Output Directory")
    public String outputDir = "";

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
            logger.info("Error: " + e.getLocalizedMessage());
            jCommander.usage();
        }

    }

    public void run() {
        Path startPath = Paths.get(sourceDir);

        try {

            Files.walkFileTree(startPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                            File file = new File(path.toAbsolutePath().toString());
                            processFile(file);
                            return FileVisitResult.CONTINUE;
                        }
                    });

            if (chunkCurrent > 0) {
                this.sendNLPServer(turSNJobItems, chunkTotal);
                turSNJobItems = new TurSNJobItems();
                chunkCurrent = 0;
            }
        } catch (IOException ioe) {
            logger.error(ioe);
        }

    }

    private void processFile(File file) throws IOException {
        TurSNJobItem turSNJobItem = new TurSNJobItem();
        turSNJobItem.setTurSNJobAction(TurSNJobAction.CREATE);
        Map<String, Object> attributes = new HashMap<String, Object>();

        List<String> webImagesExtensions = new ArrayList<String>(
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
            attributes.put("file", file);
            turSNJobItem.setAttributes(attributes);

            turSNJobItems.add(turSNJobItem);

            chunkTotal++;
            chunkCurrent++;
            if (chunkCurrent == chunk) {
                sendNLPServer(turSNJobItems, chunkTotal);
                turSNJobItems = new TurSNJobItems();
                chunkCurrent = 0;

            }
        }
    }

    public void sendNLPServer(TurSNJobItems turSNJobItems, int chunkTotal) throws IOException {

        int initial = 1;
        for (TurSNJobItem turSNJobItem : turSNJobItems) {

            if (chunkTotal > chunk) {
                initial = chunkTotal - chunk;
            }
            File fileItem = (File) turSNJobItem.getAttributes().get("file");

            FileBody fileBody = new FileBody(fileItem, ContentType.DEFAULT_BINARY);

            System.out.print("Processing " + initial + " to " + chunkTotal + " items\n");
            CloseableHttpClient client = HttpClients.createDefault();
            String restAPI = String.format("%s/api/nlp/%s/validate/file/blazon", turingServer, nlpInstance);
            HttpPost httpPost = new HttpPost(restAPI);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.addPart("file", fileBody);

            HttpEntity entity = builder.build();

            httpPost.setEntity(entity);

            CloseableHttpResponse response = client.execute(httpPost);
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (showOutput) {
                System.out.println(responseBody);

            }
            File outputFile = new File(String.format("%s%s%s", outputDir, File.separator, changeExtension(fileItem, "xml").getName()));
            FileUtils.writeStringToFile(outputFile, responseBody, "UTF-8");
            client.close();
            initial++;
        }
    }

    public static File changeExtension(File f, String newExtension) {
        int i = f.getName().lastIndexOf('.');
        String name = f.getName().substring(0, i);
        return new File(f.getParent(), name + "." + newExtension);
    }
}
