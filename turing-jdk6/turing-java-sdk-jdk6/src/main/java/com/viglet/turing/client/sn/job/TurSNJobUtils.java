/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.client.sn.job;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.ssl.TLSSocketConnectionFactory;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.utils.TurSNClientUtils;

/**
 * Turing Semantic Navigation Utilities.
 *
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
public class TurSNJobUtils {
    public static final String HTTPS = "https";
    public static final String POST = "POST";
    private static final Logger logger = Logger.getLogger(TurSNJobUtils.class.getName());
    private static final String TYPE_ATTRIBUTE = "type";
    private static final String PROVIDER_ATTRIBUTE = "source_apps";
    private static final String UTF_8 = "UTF-8";
    private static final String ACCEPT_HEADER = "Accept";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String ACCEPT_ENCODING_HEADER = "Accept-Encoding";
    private static final String APPLICATION_JSON = "application/json";

    public static void importItems(TurSNJobItems turSNJobItems, TurSNServer turSNServer, boolean showOutput) {
        try {
            String jsonResult = new ObjectMapper().writeValueAsString(turSNJobItems);
            String urlString = String.format("%s/api/sn/import", turSNServer.getServerURL());
            if (showOutput) {
                System.out.println(jsonResult);
            }
            URL url = new URL(null, urlString, new sun.net.www.protocol.https.Handler());
            URLConnection urlConnection = url.openConnection();
            if (urlString.toLowerCase().startsWith(HTTPS)) {
                ((HttpsURLConnection) urlConnection).setSSLSocketFactory(new TLSSocketConnectionFactory());
            }
            urlConnection.setRequestProperty(ACCEPT_HEADER, APPLICATION_JSON);
            urlConnection.setRequestProperty(CONTENT_TYPE_HEADER, APPLICATION_JSON);
            urlConnection.setRequestProperty(ACCEPT_ENCODING_HEADER, UTF_8);

            ((HttpURLConnection) urlConnection).setRequestMethod(POST);
            urlConnection.setDoOutput(true);

            TurSNClientUtils.basicAuth(urlConnection, turSNServer.getCredentials());

            OutputStream os = urlConnection.getOutputStream();
            byte[] input = jsonResult.getBytes(UTF_8);
            os.write(input, 0, input.length);
            String result = "";
            int responseCode = 0;
            try {
                responseCode = ((HttpURLConnection) urlConnection).getResponseCode();
                result = getTurResponseBody(urlConnection, responseCode);
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
            }
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(String.format("Viglet Turing Index Request URI: %s", urlString));
                logger.fine(String.format("JSON: %s", jsonResult));
                logger.fine(String.format("Viglet Turing indexer response code HTTP result is: %s", responseCode));
                logger.fine(String.format("Viglet Turing indexer response HTTP result is: %s", result));
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    private static String getTurResponseBody(URLConnection urlConnection, int result) throws IOException {
        StringBuilder responseBody = new StringBuilder();
        if (result == 200) {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                String strCurrentLine;
                while ((strCurrentLine = br.readLine()) != null) {
                    responseBody.append(strCurrentLine);
                }
            } finally {
                if (br != null) {
                    br.close();
                }
            }
        } else {
            BufferedReader br = null;
            try {
                br = new BufferedReader(new InputStreamReader(((HttpURLConnection) urlConnection).getErrorStream()));
                String strCurrentLine;
                while ((strCurrentLine = br.readLine()) != null) {
                    responseBody.append(strCurrentLine);
                }
            } finally {
                if (br != null) {
                    br.close();
                }
            }
        }
        return responseBody.toString();
    }

    public static void deleteItemsByType(TurSNServer turSNServer, String typeName) {
        final TurSNJobItems turSNJobItems = new TurSNJobItems();
        final TurSNJobItem turSNJobItem = new TurSNJobItem();
        turSNJobItem.setTurSNJobAction(TurSNJobAction.DELETE);
        turSNJobItem.setLocale(turSNServer.getLocale());
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(TYPE_ATTRIBUTE, typeName);
        attributes.put(PROVIDER_ATTRIBUTE, turSNServer.getProviderName());
        turSNJobItem.setAttributes(attributes);
        turSNJobItems.add(turSNJobItem);
        importItems(turSNJobItems, turSNServer, false);
    }
}
