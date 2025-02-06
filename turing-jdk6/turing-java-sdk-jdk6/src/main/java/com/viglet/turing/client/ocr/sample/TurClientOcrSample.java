package com.viglet.turing.client.ocr.sample;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.ocr.TurFileAttributes;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.credentials.TurUsernamePasswordCredentials;
import org.apache.log4j.Logger;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class TurClientOcrSample {
    private static final Logger log = Logger.getLogger(TurClientOcrSample.class);

    public static void main(String[] args) throws JsonProcessingException {
        if (args.length == 4) {
            String turingUrl = args[0];
            String username = args[1];
            String password = args[2];
            String fileUrl = args[3];
            System.out.println("--- Ocr Url");
            TurFileAttributes turFileAttributes = getAttributes(turingUrl, username, password, fileUrl);
            if (turFileAttributes != null) {
                System.out.println(new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(turFileAttributes));
            }
        } else {
            System.out.println("Parameters: turingUrl username password fileUrl");
        }
    }

    private static TurFileAttributes getAttributes(String turingUrl, String username, String password, String fileUrl) {
        try {
            TurSNServer turSNServer = new TurSNServer(new URL(turingUrl),
                    new TurUsernamePasswordCredentials(username, password));

            TurOcr turOcr = new TurOcr();
            if (fileUrl.toLowerCase().startsWith("http")) {
                return turOcr.processUrl(turSNServer, URI.create(fileUrl));

            } else {
                return turOcr.processFile(turSNServer, new File(fileUrl));
            }

        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }
        return new TurFileAttributes();
    }
}