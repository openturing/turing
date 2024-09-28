package com.viglet.turing.sprinklr.client.service.token;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.*;
import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

@Slf4j
public class TurSprinklrTokenService {
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String GRANT_TYPE = "grant_type";
    private static final String CLIENT_CREDENTIALS = "client_credentials";
    private static final String SPRINKLR_TOKEN_SERVICE = "https://api2.sprinklr.com/prod2/oauth/token";
    private static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    private static final String POST = "POST";
    private static final String CONTENT_TYPE = "Content-Type";
    public static final String ACCESS_TOKEN_SER = "store/accessToken.ser";
    private final TurSprinklrSecretKey turSprinklrSecretKey;

    public TurSprinklrTokenService(TurSprinklrSecretKey turSprinklrSecretKey) {
        this.turSprinklrSecretKey = turSprinklrSecretKey;
    }

    public TurSprinklrAccessToken getAccessToken() {
        File accessTokenFile = new File(ACCESS_TOKEN_SER);
        if (accessTokenFile.exists()) {
            TurSprinklrAccessToken turSprinklrAccessToken = deserializeAccessToken();
            return turSprinklrAccessToken != null && turSprinklrAccessToken.getExpirationDate().after(new Date()) ?
                    serializeAccessToken(turSprinklrSecretKey) :
                    deserializeAccessToken();
        } else {
            return serializeAccessToken(turSprinklrSecretKey);
        }
    }

    private TurSprinklrAccessToken serializeAccessToken(TurSprinklrSecretKey turSprinklrSecretKey) {
        TurSprinklrAccessToken turSprinklrAccessToken = generateAccessToken(turSprinklrSecretKey);
        if (turSprinklrAccessToken != null) {
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(ACCESS_TOKEN_SER))) {
                out.writeObject(turSprinklrAccessToken);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        return turSprinklrAccessToken;
    }

    private TurSprinklrAccessToken deserializeAccessToken() {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(ACCESS_TOKEN_SER))) {
            return (TurSprinklrAccessToken) in.readObject();
        } catch (IOException | ClassNotFoundException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private TurSprinklrAccessToken generateAccessToken(TurSprinklrSecretKey turSprinklrSecretKey) {
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();
        RequestBody formBody = new FormBody.Builder()
                .add(CLIENT_ID, turSprinklrSecretKey.getApiKey())
                .add(CLIENT_SECRET, turSprinklrSecretKey.getSecretKey())
                .add(GRANT_TYPE, CLIENT_CREDENTIALS)
                .build();
        Request request = new Request.Builder()
                .url(SPRINKLR_TOKEN_SERVICE)
                .method(POST, formBody)
                .addHeader(CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED)
                .build();
        try (Response response = client.newCall(request).execute()) {
            return Optional.ofNullable(response.body()).map(responseBody -> {
                try {
                    TurSprinklrAccessToken turSprinklrAccessToken = new ObjectMapper()
                            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                            .readValue(response.body().string(), TurSprinklrAccessToken.class);
                    turSprinklrAccessToken.setApiKey(turSprinklrSecretKey.getApiKey());
                    turSprinklrAccessToken.setExpirationDate(getExprirationDate(turSprinklrAccessToken));
                    turSprinklrAccessToken.setEnvironment(turSprinklrSecretKey.getEnvironment());
                    return turSprinklrAccessToken;
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                    return null;
                }
            }).orElse(null);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private static Date getExprirationDate(TurSprinklrAccessToken turSprinklrToken) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.SECOND, turSprinklrToken.getExpiresIn());
        return cal.getTime();
    }
}
