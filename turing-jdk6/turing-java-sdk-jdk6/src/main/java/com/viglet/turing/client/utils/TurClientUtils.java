package com.viglet.turing.client.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TurClientUtils {
    private static final Logger logger = Logger.getLogger(TurClientUtils.class.getName());

    public static URL getURL(String endpoint) throws MalformedURLException {
        return (endpoint.toLowerCase().startsWith("https")) ?
                new URL(null, endpoint, new sun.net.www.protocol.https.Handler()) :
                new URL(null, endpoint);
    }
    public static String openConnectionAndRequest(URLConnection urlConnection) {
        return executeQueryRequest(urlConnection);
    }

    private static String executeQueryRequest(URLConnection urlConnection) {
        try {
            int responseCode = ((HttpURLConnection) urlConnection).getResponseCode();
            return getTurResponseBody(urlConnection, responseCode);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return null;
    }

    public static String getTurResponseBody(URLConnection urlConnection, int result) throws IOException {
        StringBuilder responseBody = new StringBuilder();
        if (result == 200) {
            BufferedReader br = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            String strCurrentLine;
            while ((strCurrentLine = br.readLine()) != null) {
                responseBody.append(strCurrentLine);
            }
        } else {
            BufferedReader br = new BufferedReader(new InputStreamReader(((HttpURLConnection) urlConnection).getErrorStream()));
            String strCurrentLine;
            while ((strCurrentLine = br.readLine()) != null) {
                responseBody.append(strCurrentLine);
            }
        }
        ((HttpURLConnection) urlConnection).disconnect();
        return responseBody.toString();
    }
}
