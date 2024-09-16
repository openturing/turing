/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viglet.turing.client.sn.sample;

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
    private static final String TURING_URL = "http://localhost:2700";
    private static final String TURING_API_KEY = "9c29eb9697a642349ddedcec9";
    public static final String URL = "https://www.princexml.com/samples/invoice/invoicesample.pdf";

    public static void main(String[] args) {
        try {
            TurServer turSNServer = new TurServer(URI.create(TURING_URL).toURL(),
                    new TurApiKeyCredentials(TURING_API_KEY));
            log.info("--- Ocr Url");
            TurFileAttributes turFileAttributes = TurOcr.processUrl(turSNServer, URI.create(URL).toURL(),
                    true);
            log.info(turFileAttributes.toString());
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }
    }
}
