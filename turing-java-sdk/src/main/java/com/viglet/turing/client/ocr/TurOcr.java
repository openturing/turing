package com.viglet.turing.client.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.utils.TurClientUtils;
import com.viglet.turing.commons.file.TurFileAttributes;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.entity.mime.FileBody;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;

@Slf4j
public class TurOcr {

    public static final int TIMEOUT_MINUTES = 5;
    public static final String API_OCR_URL = "%s/api/ocr/url";
    public static final String API_OCR_FILE = "%s/api/ocr/file";
    public static final String FILE = "file";
    public static final String URL = "url";
    private final PoolingHttpClientConnectionManager pool;
    public TurOcr() {
       pool = setConnectionManager();
    }

    public TurFileAttributes processFile(TurServer turServer, File file, boolean showOutput) {
        return getTurFileAttributes(turServer,
                getRequestEntity(file),
                String.format(API_OCR_FILE, turServer.getServerUrl()),
                showOutput);
    }

    private TurFileAttributes getTurFileAttributes(TurServer turServer, HttpEntity requestEntity,
                                                          String endpoint,
                                                          boolean showOutput) {

        try (CloseableHttpClient client = HttpClients
                     .custom()
                     .setConnectionManager(pool)
                     .build();
             HttpEntity entity = requestEntity
        ) {
            HttpPost httpPost = new HttpPost(endpoint);
            httpPost.setEntity(entity);
            TurClientUtils.authentication(httpPost, turServer.getApiKey());
            String responseBody = client.execute(httpPost, response -> {
                log.info("Request Status {} - {}", response.getCode(), endpoint);
                HttpEntity responseEntity = response.getEntity();
                return responseEntity != null ? EntityUtils.toString(responseEntity) : null;
            });
            TurFileAttributes turFileAttributes = new ObjectMapper().readValue(responseBody, TurFileAttributes.class);
            if (showOutput) {
                log.info(turFileAttributes.toString());
            }
            return turFileAttributes;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return new TurFileAttributes();
        }
    }

    private static PoolingHttpClientConnectionManager setConnectionManager() {
        ConnectionConfig config = ConnectionConfig.custom()
                .setSocketTimeout(Timeout.ofMinutes(TIMEOUT_MINUTES))
                .setConnectTimeout(Timeout.ofMinutes(TIMEOUT_MINUTES))
                .setTimeToLive(TimeValue.ofMinutes(TIMEOUT_MINUTES))
                .build();
        return PoolingHttpClientConnectionManagerBuilder
                .create()
                .setDefaultConnectionConfig(config)
                .build();
    }

    private static HttpEntity getRequestEntity(File file) {
        return MultipartEntityBuilder.create()
                .addPart(FILE, new FileBody(file, ContentType.DEFAULT_BINARY))
                .build();
    }

    public TurFileAttributes processUrl(TurServer turServer, URI url, boolean showOutput) {
        return getTurFileAttributes(turServer,
                getRequestEntity(url),
                String.format(API_OCR_URL, turServer.getServerUrl()),
                showOutput);
    }

    private static StringEntity getRequestEntity(URI url) {
        return new StringEntity(
                new JSONObject().put(URL, url.toString()).toString(),
                ContentType.APPLICATION_JSON);
    }
}
