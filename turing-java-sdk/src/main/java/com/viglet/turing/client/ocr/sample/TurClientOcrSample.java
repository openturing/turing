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

import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.commons.file.TurFileAttributes;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URI;

/**
 * Sample code to use this SDK.
 *
 * @author Alexandre Oliveira
 * @since 0.3.9
 */
@Slf4j
public class TurClientOcrSample {
    public static void main(String[] args) {
        if (args.length == 3) {
            String turingUrl = args[0];
            String apiKey = args[1];
            String fileUrl = args[2];
            try {
                TurServer turSNServer = new TurServer(URI.create(turingUrl).toURL(),
                        new TurApiKeyCredentials(apiKey));
                log.info("--- Ocr Url");
                TurOcr turOcr = new TurOcr();
                TurFileAttributes turFileAttributes = turOcr.processUrl(turSNServer, URI.create(fileUrl).toURL(),
                        true);
                log.info(turFileAttributes.toString());
            } catch (MalformedURLException e) {
                log.error(e.getMessage(), e);
            }
        } else {
            log.info("Parameters: turingUrl apiKey fileUrl");
        }
    }
}
