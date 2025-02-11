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

package com.viglet.turing.client.ocr.sample;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.commons.file.TurFileAttributes;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URI;

/**
 * Sample code to use this SDK.
 *
 * @author Alexandre Oliveira
 * @since 0.3.9
 */
@Slf4j
public class TurClientOcrSample {

    public static final String HTTP = "http";

    public static void main(String[] args) throws JsonProcessingException {
        if (args.length == 4) {
            String turingUrl = args[0];
            String apiKey = args[1];
            String fileUrl = args[3];
            log.info("--- Ocr Url");
            TurFileAttributes turFileAttributes = getAttributes(turingUrl, apiKey, fileUrl);
            if (turFileAttributes != null) {
                log.info(new ObjectMapper().writer().withDefaultPrettyPrinter().writeValueAsString(turFileAttributes));
            }
        } else {
            log.info("Parameters: turingUrl apiKey [file Or Url]");
        }
    }

    private static TurFileAttributes getAttributes(String turingUrl, String apikey, String fileUrl) {
        TurServer turServer = new TurServer(URI.create(turingUrl),
                new  TurApiKeyCredentials(apikey));
        TurOcr turOcr = new TurOcr();
        if (fileUrl.toLowerCase().startsWith(HTTP)) {
            return turOcr.processUrl(turServer, URI.create(fileUrl), false);

        } else {
            return turOcr.processFile(turServer, new File(fileUrl), false);
        }
    }
}
