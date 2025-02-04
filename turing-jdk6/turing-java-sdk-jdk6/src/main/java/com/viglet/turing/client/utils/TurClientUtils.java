package com.viglet.turing.client.utils;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TurClientUtils {
    private static final Logger logger = Logger.getLogger(TurClientUtils.class.getName());
    public static String openConnectionAndRequest(HttpsURLConnection httpsURLConnection) {
        return executeQueryRequest(httpsURLConnection);
    }

    private static String executeQueryRequest(HttpsURLConnection httpsURLConnection) {
        try {
            int responseCode = httpsURLConnection.getResponseCode();
            return getTurResponseBody(httpsURLConnection, responseCode);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return null;
    }

    public static String getTurResponseBody(HttpsURLConnection httpsURLConnection, int result) throws IOException {
        StringBuilder responseBody = new StringBuilder();
        if (result == 200) {
            BufferedReader br = new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()));
            String strCurrentLine;
            while ((strCurrentLine = br.readLine()) != null) {
                responseBody.append(strCurrentLine);
            }
        } else {
            BufferedReader br = new BufferedReader(new InputStreamReader(httpsURLConnection.getErrorStream()));
            String strCurrentLine;
            while ((strCurrentLine = br.readLine()) != null) {
                responseBody.append(strCurrentLine);
            }
        }
        httpsURLConnection.disconnect();
        return responseBody.toString();
    }
}
