package com.viglet.turing.client.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.utils.TurSNClientUtils;
import com.viglet.turing.client.ssl.TLSSocketConnectionFactory;
import com.viglet.turing.client.utils.TurClientUtils;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TurOcr {
    private static final Logger logger = Logger.getLogger(TurOcr.class.getName());
    private static final String UTF_8 = "UTF-8";
    private static final String ACCEPT_HEADER = "Accept";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String ACCEPT_ENCODING_HEADER = "Accept-Encoding";
    private static final String APPLICATION_JSON = "application/json";
    public static final String API_OCR_URL = "%s/api/ocr/url";
    public static final String API_OCR_FILE = "%s/api/ocr/file";
    public static final String FILE = "file";
    public static final String URL = "url";
    public static final String CONNECTION = "Connection";
    public static final String CACHE_CONTROL = "Cache-Control";
    public static final String POST = "POST";
    public static final String KEEP_ALIVE = "Keep-Alive";
    public static final String NO_CACHE = "no-cache";
    public static final String CRLF = "\r\n";
    public static final String TWO_HYPHENS = "--";
    public static final String BOUNDARY = "*****";
    public static final String MULTIPART = "multipart/form-data";
    public static final String HTTPS = "https";

    public TurFileAttributes processUrl(TurSNServer turServer, URI url) {
        return getTurFileAttributes(turServer,
                new JSONObject().put(URL, url.toString()),
                String.format(API_OCR_URL, turServer.getServerURL()));
    }

    public TurFileAttributes processFile(TurSNServer turServer, File file) {
        return getTurFileAttributes(turServer,
                file,
                String.format(API_OCR_FILE, turServer.getServerURL()));
    }

    private TurFileAttributes getTurFileAttributes(TurSNServer turServer, JSONObject jsonObject,
                                                   String endpoint) {
        try {
            URLConnection urlConnection = getURLConnection(turServer, endpoint);
            urlConnection.setRequestProperty(CONTENT_TYPE_HEADER, APPLICATION_JSON);
            OutputStream os = urlConnection.getOutputStream();
            byte[] input = jsonObject.toString().getBytes(UTF_8);
            os.write(input, 0, input.length);

            return new ObjectMapper().readValue(TurClientUtils.openConnectionAndRequest(urlConnection),
                    TurFileAttributes.class);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return null;
    }

    private static URLConnection getURLConnection(TurSNServer turServer, String endpoint) throws IOException {
        URL url = TurClientUtils.getURL(endpoint);
        URLConnection urlConnection = url.openConnection();
        if (endpoint.toLowerCase().startsWith(HTTPS)) {
            ((HttpsURLConnection) urlConnection).setSSLSocketFactory(new TLSSocketConnectionFactory());
        }
        urlConnection.setRequestProperty(ACCEPT_HEADER, APPLICATION_JSON);
        urlConnection.setRequestProperty(ACCEPT_ENCODING_HEADER, UTF_8);
        urlConnection.setRequestProperty(CONNECTION, KEEP_ALIVE);
        urlConnection.setRequestProperty(CACHE_CONTROL, NO_CACHE);
        ((HttpURLConnection) urlConnection).setRequestMethod(POST);
        urlConnection.setUseCaches(false);
        urlConnection.setDoOutput(true);
        TurSNClientUtils.basicAuth(urlConnection, turServer.getCredentials());

        return urlConnection;
    }

    private TurFileAttributes getTurFileAttributes(TurSNServer turServer, File file,
                                                   String endpoint) {
        String attachmentFileName = file.getName();
        try {
            URLConnection httpsURLConnection = getURLConnection(turServer, endpoint);
            httpsURLConnection.setRequestProperty(
                    CONTENT_TYPE_HEADER, MULTIPART + ";boundary=" + BOUNDARY);
            DataOutputStream request = new DataOutputStream(
                    httpsURLConnection.getOutputStream());
            request.writeBytes(TWO_HYPHENS + BOUNDARY + CRLF);
            request.writeBytes("Content-Disposition: form-data; name=\"" +
                    FILE + "\";filename=\"" +
                    attachmentFileName + "\"" + CRLF);
            request.writeBytes(CRLF);

            byte[] byteArray = new byte[(int) file.length()];
            FileInputStream inputStream = new FileInputStream(file);
            inputStream.read(byteArray);

            request.write(byteArray);
            request.writeBytes(CRLF);
            request.writeBytes(TWO_HYPHENS + BOUNDARY +
                    TWO_HYPHENS + CRLF);
            request.flush();
            request.close();

            return new ObjectMapper().readValue(TurClientUtils.openConnectionAndRequest(httpsURLConnection),
                    TurFileAttributes.class);
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return new TurFileAttributes();
    }
}
