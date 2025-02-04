package com.viglet.turing.client.ocr.sample;

import com.viglet.turing.client.ocr.TurFileAttributes;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.credentials.TurUsernamePasswordCredentials;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.logging.Logger;

public class TurClientOcrSample {
    private static final Logger logger = Logger.getLogger(TurClientOcrSample.class.getName());
    public static void main(String[] args) throws MalformedURLException {
        if (args.length == 4) {
            String turingUrl = args[0];
            String username = args[1];
            String password = args[2];
            String fileUrl = args[3];
            TurSNServer turSNServer = new TurSNServer(new URL(turingUrl),
                    new TurUsernamePasswordCredentials(username, password));
            logger.fine("--- Ocr Url");
            TurOcr turOcr = new TurOcr();
            TurFileAttributes turFileAttributes = turOcr.processUrl(turSNServer, URI.create(fileUrl));
            logger.fine(turFileAttributes.toString());
        } else {
            logger.fine("Parameters: turingUrl apiKey fileUrl");
        }
    }
}