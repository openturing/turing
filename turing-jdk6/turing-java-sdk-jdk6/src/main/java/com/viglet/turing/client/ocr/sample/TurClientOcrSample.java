package com.viglet.turing.client.ocr.sample;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.ocr.TurFileAttributes;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.credentials.TurUsernamePasswordCredentials;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

public class TurClientOcrSample {
    public static void main(String[] args) throws MalformedURLException, JsonProcessingException {
        if (args.length == 4) {
            String turingUrl = args[0];
            String username = args[1];
            String password = args[2];
            String fileUrl = args[3];
            TurSNServer turSNServer = new TurSNServer(new URL(turingUrl),
                    new TurUsernamePasswordCredentials(username, password));
            System.out.println("--- Ocr Url");
            TurOcr turOcr = new TurOcr();
            TurFileAttributes turFileAttributes = turOcr.processUrl(turSNServer, URI.create(fileUrl));
            if (turFileAttributes != null) {
                System.out.println( new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(turFileAttributes));
            }
        } else {
            System.out.println("Parameters: turingUrl username password fileUrl");
        }
    }
}